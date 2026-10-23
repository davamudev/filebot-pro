package dev.davamu.filebotpro.ui.rename;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.media.MediaDetection.*;
import static dev.davamu.filebotpro.media.XattrMetaInfo.*;
import static dev.davamu.filebotpro.util.ExceptionUtilities.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import java.util.logging.Level;
import java.util.stream.Stream;

import javax.swing.AbstractAction;

import dev.davamu.filebotpro.CacheManager;
import dev.davamu.filebotpro.HistorySpooler;
import dev.davamu.filebotpro.NativeRenameAction;
import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.StandardRenameAction;
import dev.davamu.filebotpro.UserFiles;
import dev.davamu.filebotpro.platform.mac.MacAppUtilities;
import dev.davamu.filebotpro.platform.windows.SessionEndHandler;
import dev.davamu.filebotpro.similarity.Match;
import dev.davamu.filebotpro.util.ui.ProgressMonitor;
import dev.davamu.filebotpro.util.ui.ProgressMonitor.ProgressWorker;

class RenameAction extends AbstractAction {

	public static final String RENAME_ACTION = "RENAME_ACTION";

	private final RenameModel model;

	public RenameAction(RenameModel model) {
		this.model = model;
		resetValues();
	}

	public void resetValues() {
		putValue(RENAME_ACTION, StandardRenameAction.MOVE);
		putValue(NAME, "Rename");
		putValue(SMALL_ICON, ResourceManager.getIcon("action.rename"));
	}

	@Override
	public void actionPerformed(ActionEvent evt) {
		if (model.names().isEmpty() || model.files().isEmpty()) {
			log.info("Nothing to rename. New Names is empty. Please <Fetch Data> first.");
			return;
		}

		Window window = getWindow(evt.getSource());
		withWaitCursor(window, () -> {
			// flush all memory caches to disk (before starting any long running file system operations that might be cancelled by the user)
			CacheManager.getInstance().flushAll();

			// prepare rename map (abort and notify the user if background computation is still in progress)
			Map<File, File> renameMap = null;
			try {
				renameMap = removeMissingFiles(model.getRenameMap());
				if (renameMap.isEmpty()) {
					return;
				}
				renameMap = validate(renameMap, window);
			} catch (Exception e) {
				log.log(Level.WARNING, e::getMessage);
			}

			if (renameMap == null || renameMap.isEmpty()) {
				return;
			}

			List<Match<Object, File>> matches = new ArrayList<Match<Object, File>>(model.matches());
			StandardRenameAction action = (StandardRenameAction) getValue(RENAME_ACTION);

			// start processing
			Map<File, File> renameLog = new LinkedHashMap<File, File>();

			// Windows: ask the user to wait before logging off or shutting down until all files are renamed
			try (SessionEndHandler.Block shutdownBlock = SessionEndHandler.block("Renaming files")) {
				if (useNativeShell() && NativeRenameAction.isSupported(action)) {
					// call on EDT
					NativeRenameWorker worker = new NativeRenameWorker(renameMap, renameLog, NativeRenameAction.valueOf(action.name()));
					worker.call(null, null, null);
				} else {
					// call and wait
					StandardRenameWorker worker = new StandardRenameWorker(renameMap, renameLog, action);
					String message = String.format("%s %d %s. This may take a while.", action.getDisplayVerb(), renameMap.size(), renameMap.size() == 1 ? "file" : "files");
					ProgressMonitor.runTask(action.getDisplayName(), message, worker).get();
				}
			} catch (CancellationException e) {
				debug.finest(e::toString);
			} catch (Throwable e) {
				log.log(Level.SEVERE, e, cause(getRootCause(e)));
			}

			// abort if nothing happened
			if (renameLog.isEmpty()) {
				return;
			}

			log.info(String.format("%d files renamed.", renameLog.size()));

			// remove renamed matches
			renameLog.forEach((from, to) -> {
				model.matches().remove(model.files().indexOf(from));
			});

			// history, xattr and folder cleanup may take a while (e.g. many files on a network share)
			Map<File, File> mapping = renameMap;
			ProgressWorker<Void> postProcess = (message, progress, cancelled) -> {
				message.accept("Saving rename history");
				HistorySpooler.getInstance().append(renameLog.entrySet());
				HistorySpooler.getInstance().commit();

				// in-memory xattr metadata of both paths is outdated now
				renameLog.forEach((from, to) -> xattr.invalidate(from, resolve(from, to)));

				// store xattr
				storeMetaInfo(mapping, matches, message, progress, cancelled);

				// delete empty folders
				if (action == StandardRenameAction.MOVE && !cancelled.get()) {
					message.accept("Deleting empty folders");
					progress.accept(0L, 0L);
					deleteEmptyFolders(renameLog);
				}
				return null;
			};

			try {
				ProgressMonitor.runTask(action.getDisplayName(), "Saving history and metadata", postProcess).get();
			} catch (CancellationException e) {
				debug.finest(e::toString);
			} catch (Throwable e) {
				log.log(Level.WARNING, e, cause(getRootCause(e)));
			}
		});
	}

	private static void storeMetaInfo(Map<File, File> renameMap, List<Match<Object, File>> matches, Consumer<String> message, BiConsumer<Long, Long> progress, Supplier<Boolean> cancelled) {
		long done = 0;

		// write metadata into xattr if xattr is enabled
		for (Match<Object, File> match : matches) {
			if (cancelled.get()) {
				return;
			}
			progress.accept(done++, (long) matches.size());

			File file = match.getCandidate();
			Object info = match.getValue();
			File destination = renameMap.get(file);
			if (info != null && destination != null) {
				destination = resolve(file, destination);
				if (destination.isFile()) {
					message.accept(String.format("%d of %d · %s", done, matches.size(), destination.getName()));
					String original = file.getName();
					debug.finest(format("Store xattr: [%s, %s] => %s", info, original, destination));
					xattr.setMetaInfo(destination, info, original);
				}
			}
		}
	}

	private void deleteEmptyFolders(Map<File, File> renameMap) {
		// collect empty folders and files in reverse order
		Set<File> deleteFiles = new TreeSet<File>();

		renameMap.forEach((s, d) -> {
			File sourceFolder = s.getParentFile();
			File destinationFolder = resolve(s, d).getParentFile();

			// destination folder is the source, or is inside the source folder
			if (d.getParentFile() == null || destinationFolder.getPath().startsWith(sourceFolder.getPath())) {
				return;
			}

			try {
				// guess affected folder depth
				int tailSize = listStructurePathTail(d.getParentFile()).size();

				for (int i = 0; i < tailSize && !isStructureRoot(sourceFolder); sourceFolder = sourceFolder.getParentFile(), i++) {
					File[] children = sourceFolder.listFiles();
					if (children == null || !stream(children).allMatch(f -> deleteFiles.contains(f) || isThumbnailStore(f))) {
						return;
					}

					stream(children).forEach(deleteFiles::add);
					deleteFiles.add(sourceFolder);
				}
			} catch (Exception e) {
				debug.warning(e::toString);
			}
		});

		// use system trash to delete left-behind empty folders / hidden files
		try {
			for (File file : deleteFiles) {
				if (file.exists()) {
					UserFiles.trash(file);
				}
			}
		} catch (Throwable e) {
			debug.log(Level.WARNING, e, e::getMessage);
		}
	}

	/**
	 * Files that have been moved, renamed or deleted by another program since they were loaded can't be renamed anymore,
	 * so they are removed from the rename map and from the list instead of failing the whole batch.
	 */
	private Map<File, File> removeMissingFiles(Map<File, File> renameMap) {
		List<File> missing = renameMap.keySet().stream().filter(RenameAction::isMissing).collect(toList());
		if (missing.isEmpty()) {
			return renameMap;
		}

		missing.forEach(f -> {
			renameMap.remove(f);
			model.matches().remove(model.files().indexOf(f));
		});

		boolean single = missing.size() == 1;
		String names = missing.stream().limit(5).map(File::getName).collect(joining(", ")) + (missing.size() > 5 ? ", …" : "");
		log.warning(String.format("%d %s no longer %s and %s been removed from the list: %s", missing.size(), single ? "file" : "files", single ? "exists" : "exist", single ? "has" : "have", names));
		return renameMap;
	}

	private static boolean isMissing(File file) {
		// broken symlinks still exist
		return !Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS);
	}

	private Map<File, File> validate(Map<File, File> renameMap, Window parent) {
		// rename map values as modifiable list
		List<File> destinationPathView = new AbstractList<File>() {

			private File[] keyIndex = renameMap.keySet().toArray(new File[0]);

			@Override
			public File get(int i) {
				return renameMap.get(keyIndex[i]);
			}

			@Override
			public File set(int i, File value) {
				return renameMap.put(keyIndex[i], value);
			}

			@Override
			public int size() {
				return keyIndex.length;
			}
		};

		if (ValidateDialog.validate(parent, destinationPathView)) {

			if (ConflictDialog.check(parent, renameMap)) {
				return renameMap;
			}
		}

		// return empty list if validation was cancelled
		return emptyMap();
	}

	protected static class StandardRenameWorker implements ProgressWorker<Map<File, File>> {

		private Map<File, File> renameMap;
		private Map<File, File> renameLog;

		private StandardRenameAction action;

		public StandardRenameWorker(Map<File, File> renameMap, Map<File, File> renameLog, StandardRenameAction action) {
			this.renameMap = renameMap;
			this.renameLog = renameLog;
			this.action = action;
		}

		@Override
		public Map<File, File> call(Consumer<String> message, BiConsumer<Long, Long> progress, Supplier<Boolean> cancelled) throws Exception {
			// copying takes time proportional to the file size, everything else takes about the same time for each file
			boolean copy = action == StandardRenameAction.COPY || action == StandardRenameAction.DUPLICATE || action == StandardRenameAction.CLONE;
			ToLongFunction<File> work = f -> copy && f.isFile() ? Math.max(f.length(), 1) : 1;

			long total = renameMap.keySet().stream().mapToLong(work).sum();
			long done = 0;
			int count = 0;

			for (Entry<File, File> mapping : renameMap.entrySet()) {
				if (cancelled.get()) {
					return renameLog;
				}

				message.accept(String.format("%d of %d · %s", ++count, renameMap.size(), mapping.getKey().getName()));
				progress.accept(done, total);

				// rename file, throw exception on failure
				File source = mapping.getKey();
				File destination = resolve(mapping.getKey(), mapping.getValue());
				long size = work.applyAsLong(source);

				// skip files that have disappeared while renaming the previous ones
				if (isMissing(source)) {
					log.warning(message("File no longer exists", source));
					done += size;
					continue;
				}

				if (!equalsCaseSensitive(source, destination)) {
					action.rename(source, destination);
				}

				// remember successfully renamed matches for history entry and possible revert
				renameLog.put(mapping.getKey(), mapping.getValue());

				done += size;
				progress.accept(done, total);
			}

			return renameLog;
		}
	}

	protected static class NativeRenameWorker implements ProgressWorker<Map<File, File>> {

		private Map<File, File> renameMap;
		private Map<File, File> renameLog;

		private NativeRenameAction action;

		public NativeRenameWorker(Map<File, File> renameMap, Map<File, File> renameLog, NativeRenameAction action) {
			this.renameMap = renameMap;
			this.renameLog = renameLog;
			this.action = action;
		}

		@Override
		public Map<File, File> call(Consumer<String> message, BiConsumer<Long, Long> progress, Supplier<Boolean> cancelled) throws Exception {
			// prepare delta, ignore files already named as desired
			Map<File, File> renamePlan = new LinkedHashMap<File, File>();

			renameMap.forEach((from, to) -> {
				// resolve relative paths
				to = resolve(from, to);

				if (!equalsCaseSensitive(from, to)) {
					renamePlan.put(from, to);
				}
			});

			// call native shell move/copy
			try {
				action.rename(renamePlan);
			} catch (CancellationException e) {
				debug.finest(e::getMessage);
			}

			// confirm results
			renameMap.forEach((from, to) -> {
				// resolve relative paths
				if (resolve(from, to).exists()) {
					renameLog.put(from, to);
				}
			});

			return renameLog;
		}

	}

}
