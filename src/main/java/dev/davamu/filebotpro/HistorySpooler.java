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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

import dev.davamu.filebotpro.History.Element;

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

	private final History sessionHistory = new History();

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

		history.addAll(sessionHistory.sequences());
		return history;
	}

	public synchronized void commit() {
		if (sessionHistory.sequences().isEmpty() || !persistentHistoryEnabled) {
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
				}
			}

			// write new combined history
			history.addAll(sessionHistory.sequences());
			writePersistentHistory(history);

			sessionHistory.clear();
			sessionHistoryTotalSize = 0;
			persistentHistoryTotalSize = history.totalSize();
		} catch (Exception e) {
			debug.log(Level.SEVERE, "Failed to write history file", e);
		}
	}

	public synchronized void clear() throws IOException {
		log.fine("* Delete " + persistentHistoryFile);

		try (FileLock lock = lock()) {
			Files.deleteIfExists(persistentHistoryFile.toPath());
		}

		persistentHistory = null;
		sessionHistory.clear();
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
		List<Element> sequence = new ArrayList<Element>();

		for (Entry<File, File> element : elements) {
			File k = element.getKey();
			File v = element.getValue();

			if (k != null && v != null) {
				sequence.add(new Element(k.getName(), v.getPath(), k.getParentFile()));
			}
		}

		if (sequence.size() > 0) {
			sessionHistory.add(sequence, session); // append to session history
			sessionHistoryTotalSize += sequence.size();
		}
	}

	public synchronized void append(History importHistory) {
		sessionHistory.merge(importHistory);
	}

	public synchronized History getSessionHistory() {
		return new History(sessionHistory.sequences());
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
