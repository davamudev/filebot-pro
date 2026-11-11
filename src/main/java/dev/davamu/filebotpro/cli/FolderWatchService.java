package dev.davamu.filebotpro.cli;

import static java.nio.file.StandardWatchEventKinds.*;
import static java.util.Collections.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import dev.davamu.filebotpro.util.DefaultThreadFactory;
import dev.davamu.filebotpro.util.Timer;

public abstract class FolderWatchService implements Closeable {

	private final Collection<File> commitSet = new HashSet<File>();

	private final ExecutorService processor = Executors.newSingleThreadExecutor();
	private final ExecutorService watcher = Executors.newSingleThreadExecutor(new DefaultThreadFactory("FolderWatchService"));

	// one watch service (one inotify instance on Linux) for all folders, so large folder trees don't run into the per-user limit of instances
	private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<WatchKey, Path>();
	private WatchService watchService;

	private long commitDelay = 500; // 0.5 s
	private boolean commitPerFolder = true;
	private final Timer commitTimer = new Timer() {

		@Override
		public void run() {
			synchronized (processor) {
				commit();
			}
		}
	};

	private final boolean watchTree;

	public FolderWatchService(boolean watchTree) {
		this.watchTree = watchTree;
	}

	public synchronized void setCommitPerFolder(boolean enabled) {
		this.commitPerFolder = enabled;
	}

	public synchronized void setCommitDelay(long commitDelay) {
		if (commitDelay < 0)
			throw new IllegalArgumentException("Delay must not be negativ");

		this.commitDelay = commitDelay;
		resetCommitTimer();
	}

	public synchronized void resetCommitTimer() {
		commitTimer.set(commitDelay, TimeUnit.MILLISECONDS, false);
	}

	public synchronized void commit() {
		final SortedSet<File> files = new TreeSet<File>();

		synchronized (commitSet) {
			for (File it : commitSet) {
				if (it.isFile()) {
					files.add(it);
				}
			}
			commitSet.clear();
		}

		if (files.isEmpty()) {
			return;
		}

		// divide into commit batches per folder if required
		final Map<File, ? extends Collection<File>> commitBatches = commitPerFolder ? mapByFolder(files) : singletonMap((File) null, files);

		processor.submit(new Runnable() {

			@Override
			public void run() {
				synchronized (processor) {
					for (Entry<File, ? extends Collection<File>> it : commitBatches.entrySet()) {
						processCommitSet(it.getValue().toArray(new File[0]), it.getKey());
					}
				}
			}
		});
	}

	public abstract void processCommitSet(File[] files, File dir);

	public void watchFolder(File folder) throws IOException {
		if (!watchTree) {
			startWatch(folder);
			return;
		}

		watchFolderTree(folder);
	}

	private void watchFolderTree(File root) throws IOException {
		// hidden folders and NAS system folders (e.g. Synology @eaDir thumbnails) are not input
		for (File it : getChildren(root, f -> f.isDirectory() && NOT_HIDDEN.accept(f))) {
			watchFolderTree(it);
		}

		startWatch(root);
	}

	private void startWatch(File node) throws IOException {
		if (!node.isDirectory()) {
			throw new IllegalArgumentException("Must be a folder: " + node);
		}

		synchronized (watchKeys) {
			if (watchService == null) {
				watchService = FileSystems.getDefault().newWatchService();
				watcher.submit(this::watch);
			}

			Path path = node.toPath();
			watchKeys.put(path.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE), path);
		}
	}

	private void watch() {
		try {
			while (true) {
				WatchKey key = watchService.take();
				Path folder = watchKeys.get(key);

				if (folder != null) {
					processEvents(folder, key.pollEvents());
				}

				if (!key.reset()) {
					watchKeys.remove(key);
				}
			}
		} catch (InterruptedException | ClosedWatchServiceException e) {
			// ignore, part of an orderly shutdown
		} catch (Exception e) {
			debug.log(Level.WARNING, e.getMessage(), e);
		}
	}

	private void processEvents(Path folder, List<WatchEvent<?>> events) {
		// outside of the commitSet lock, commit() takes both locks in the opposite order
		resetCommitTimer();

		synchronized (commitSet) {
			for (WatchEvent<?> event : events) {
				if (event.kind() == OVERFLOW) {
					continue;
				}

				File file = folder.resolve(event.context().toString()).toFile();

				if (event.kind() == ENTRY_CREATE) {
					created(file);
				} else if (event.kind() == ENTRY_MODIFY) {
					modified(file);
				} else if (event.kind() == ENTRY_DELETE) {
					commitSet.remove(file);
				}
			}
		}
	}

	private void created(File file) {
		if (!file.isDirectory()) {
			commitSet.add(file);
			return;
		}

		// start watching newly created folder
		if (watchTree && NOT_HIDDEN.accept(file)) {
			try {
				commitSet.addAll(listFiles(file, FILES));
				watchFolder(file);
			} catch (IOException e) {
				debug.log(Level.SEVERE, e.getMessage(), e);
			}
		}
	}

	private void modified(File file) {
		if (!file.isDirectory()) {
			commitSet.add(file);
		}
	}

	@Override
	public synchronized void close() throws IOException {
		commitTimer.cancel();
		processor.shutdownNow();
		watcher.shutdownNow();

		synchronized (watchKeys) {
			if (watchService != null) {
				watchService.close();
			}
		}
	}

}
