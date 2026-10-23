package dev.davamu.filebotpro;

import static dev.davamu.filebotpro.Logging.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

import dev.davamu.filebotpro.History.Element;
import dev.davamu.filebotpro.History.Sequence;

public final class HistorySpooler {

	private static final HistorySpooler instance = new HistorySpooler();

	public static HistorySpooler getInstance() {
		return instance;
	}

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(HistorySpooler.getInstance()::commit, "HistorySpoolerShutdownHook")); // commit session history on shutdown
	}

	private final File persistentHistoryFile = ApplicationFolder.AppData.resolve("history.xml");
	private final File persistentHistoryLockFile = ApplicationFolder.AppData.resolve("history.lock");

	private int sessionHistoryTotalSize = 0;
	private int persistentHistoryTotalSize = -1;
	private boolean persistentHistoryEnabled = true;

	// all sequences of this process, which are kept in memory even after they have been written to the history file
	private final History sessionHistory = new History();

	// copy of each sequence of this process as it was last written to the history file, so that it can be updated when more files are added to the sequence
	private final Map<Sequence, Sequence> persistedSequences = new IdentityHashMap<Sequence, Sequence>();

	// write the history file at most once per second while files are being renamed
	private static final long COMMIT_INTERVAL = 1000;

	private boolean modified = false;
	private long lastCommit = 0;

	// identifies all rename operations of this process (e.g. movies and episodes of one -rename call), so that -revert can undo the most recent operation as a whole
	private String session = Long.toString(System.currentTimeMillis(), 36) + "-" + Long.toString(ProcessHandle.current().pid(), 36);

	// parsed history file and the file state it was parsed from, so that the file is only parsed again if it has been modified by another process
	private History persistentHistory;
	private long persistentHistoryLastModified;
	private long persistentHistoryLength;

	public synchronized History getCompleteHistory() throws IOException {
		History history = new History();

		if (persistentHistoryFile.length() > 0) {
			try (FileLock lock = lock()) {
				history.addAll(readPersistentHistory().sequences());
			} catch (IOException e) {
				debug.log(Level.SEVERE, "Failed to read history file", e);
			}
		}

		mergeSessionHistory(history);
		return history;
	}

	/**
	 * Write the session history to the history file, but only if the last commit has been a while ago (e.g. after each renamed file)
	 */
	public synchronized void commitPeriodically() {
		if (System.currentTimeMillis() - lastCommit >= COMMIT_INTERVAL) {
			commit();
		}
	}

	public synchronized void commit() {
		if (!modified || !persistentHistoryEnabled) {
			return;
		}

		try (FileLock lock = lock()) {
			History history = new History();

			// load existing history from previous sessions
			if (persistentHistoryFile.length() > 0) {
				try {
					history.addAll(readPersistentHistory().sequences());
				} catch (IOException e) {
					// never overwrite history we can't read
					File backup = backupPersistentHistory();
					log.log(Level.SEVERE, String.format("Failed to read history file: %s (backup saved to %s)", e.getMessage(), backup), e);

					// write all sequences of this process to the new history file
					persistedSequences.clear();
				}
			}

			// write new combined history
			Map<Sequence, Sequence> written = mergeSessionHistory(history);
			writePersistentHistory(history);

			persistedSequences.putAll(written);
			modified = false;
			sessionHistoryTotalSize = 0;
			persistentHistoryTotalSize = history.totalSize();
		} catch (Exception e) {
			debug.log(Level.SEVERE, "Failed to write history file", e);
		} finally {
			lastCommit = System.currentTimeMillis();
		}
	}

	/**
	 * Add new sequences of this process to the given history and update the sequences that have changed since they were written
	 *
	 * @return the copy of each sequence that has been added to the given history
	 */
	private Map<Sequence, Sequence> mergeSessionHistory(History history) {
		Map<Sequence, Sequence> copies = new IdentityHashMap<Sequence, Sequence>();

		for (Sequence sequence : sessionHistory.sequences()) {
			Sequence previous = persistedSequences.get(sequence);

			// sequence is already in the history file
			if (previous != null && previous.elements().size() == sequence.elements().size()) {
				continue;
			}

			Sequence copy = sequence.copy();
			if (previous == null || !history.replace(previous, copy)) {
				history.add(copy);
			}
			copies.put(sequence, copy);
		}

		return copies;
	}

	public synchronized void clear() throws IOException {
		log.fine("* Delete " + persistentHistoryFile);

		try (FileLock lock = lock()) {
			Files.deleteIfExists(persistentHistoryFile.toPath());
		}

		persistentHistory = null;
		sessionHistory.clear();
		persistedSequences.clear();
		modified = false;
		sessionHistoryTotalSize = 0;
		persistentHistoryTotalSize = 0;
	}

	private FileLock lock() throws IOException {
		// lock a separate file, so that the history file itself can be replaced atomically
		FileChannel channel = FileChannel.open(persistentHistoryLockFile.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
		try {
			return new ChannelLock(channel, channel.lock());
		} catch (IOException | RuntimeException e) {
			channel.close();
			throw e;
		}
	}

	private History readPersistentHistory() throws IOException {
		long lastModified = persistentHistoryFile.lastModified();
		long length = persistentHistoryFile.length();

		if (persistentHistory == null || persistentHistoryLastModified != lastModified || persistentHistoryLength != length) {
			try (InputStream in = new BufferedInputStream(Files.newInputStream(persistentHistoryFile.toPath()))) {
				persistentHistory = History.importHistory(in);
				persistentHistoryLastModified = lastModified;
				persistentHistoryLength = length;
			} catch (IOException e) {
				persistentHistory = null;
				throw e;
			}
		}

		return persistentHistory;
	}

	private void writePersistentHistory(History history) throws IOException {
		Path file = persistentHistoryFile.toPath();
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");

		try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(temp))) {
			History.exportHistory(history, out);
		}

		try {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		}

		persistentHistory = new History(history.sequences());
		persistentHistoryLastModified = persistentHistoryFile.lastModified();
		persistentHistoryLength = persistentHistoryFile.length();
	}

	private File backupPersistentHistory() {
		File backup = ApplicationFolder.AppData.resolve("history." + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".bak.xml");
		try {
			Files.copy(persistentHistoryFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
			return backup;
		} catch (Exception e) {
			debug.log(Level.SEVERE, "Failed to backup history file", e);
			return null;
		}
	}

	public synchronized void append(Map<File, File> elements) {
		append(elements.entrySet());
	}

	public synchronized void append(Iterable<Entry<File, File>> elements) {
		append(null, elements);
	}

	/**
	 * Add renamed files to the given sequence of this session (e.g. one file at a time while a batch is being renamed), or to a new sequence if the given sequence is null
	 *
	 * @return the sequence the renamed files have been added to
	 */
	public synchronized Sequence append(Sequence sequence, Iterable<Entry<File, File>> elements) {
		List<Element> list = new ArrayList<Element>();

		for (Entry<File, File> element : elements) {
			File k = element.getKey();
			File v = element.getValue();

			if (k != null && v != null) {
				list.add(new Element(k.getName(), v.getPath(), k.getParentFile()));
			}
		}

		if (list.isEmpty()) {
			return sequence;
		}

		if (sequence == null) {
			sequence = sessionHistory.add(list, session); // append to session history
		} else {
			sequence.addAll(list);
		}

		sessionHistoryTotalSize += list.size();
		modified = true;
		return sequence;
	}

	public synchronized void append(History importHistory) {
		sessionHistory.merge(importHistory);
		modified = true;
	}

	public synchronized History getSessionHistory() {
		// copy sequences, because sequences of this process may still grow
		History history = new History();
		sessionHistory.sequences().forEach(s -> history.add(s.copy()));
		return history;
	}

	public synchronized int getSessionHistoryTotalSize() {
		return sessionHistoryTotalSize;
	}

	public synchronized int getPersistentHistoryTotalSize() {
		return persistentHistoryTotalSize;
	}

	public synchronized void setPersistentHistoryEnabled(boolean persistentHistoryEnabled) {
		this.persistentHistoryEnabled = persistentHistoryEnabled;
	}

	/**
	 * Each rename in the user interface is an operation of its own, whereas all renames of a command-line call are one operation
	 */
	public synchronized void setProcessSessionEnabled(boolean enabled) {
		this.session = enabled ? session : null;
	}

	/**
	 * File lock that also closes its channel
	 */
	private static class ChannelLock extends FileLock {

		private final FileLock lock;

		public ChannelLock(FileChannel channel, FileLock lock) {
			super(channel, lock.position(), lock.size(), lock.isShared());
			this.lock = lock;
		}

		@Override
		public boolean isValid() {
			return lock.isValid();
		}

		@Override
		public void release() throws IOException {
			try {
				lock.release();
			} finally {
				channel().close();
			}
		}
	}

}
