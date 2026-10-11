package dev.davamu.filebotpro;

import static dev.davamu.filebotpro.Logging.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.logging.Level;

import com.github.benmanes.caffeine.cache.Caffeine;

import dev.davamu.filebotpro.Cache.Entry;

/**
 * A named cache: the most recently used entries are kept in memory, every entry is also stored in its own file (so the
 * data survives restarts). Entries expire a fixed time after they were stored.
 */
final class DiskCache {

	private static final int MAX_ENTRIES_IN_MEMORY = 200;

	/**
	 * Cache files live in the user folder, but treat them as untrusted anyway: only plain data types and our own classes
	 * may be deserialized.
	 */
	private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter("java.lang.*;java.util.*;java.time.*;java.math.*;java.net.URI;dev.davamu.filebotpro.**;!*");

	private final Path folder;
	private final Duration defaultTimeToLive;
	private final com.github.benmanes.caffeine.cache.Cache<Object, Entry> memory = Caffeine.newBuilder().maximumSize(MAX_ENTRIES_IN_MEMORY).build();

	DiskCache(Path folder, Duration defaultTimeToLive) {
		this.folder = folder;
		this.defaultTimeToLive = defaultTimeToLive;
	}

	Entry get(Object key) {
		Entry entry = memory.getIfPresent(key);
		if (entry == null) {
			entry = read(key);
			if (entry != null) {
				memory.put(key, entry);
			}
		}

		if (entry != null && isExpired(entry)) {
			remove(key);
			return null;
		}
		return entry;
	}

	void put(Entry entry) throws IOException {
		memory.put(entry.getKey(), entry);

		Files.createDirectories(folder);
		Path target = file(entry.getKey());
		Path temp = Files.createTempFile(folder, ".write", ".tmp");
		try {
			try (OutputStream out = Files.newOutputStream(temp); ObjectOutputStream objects = new ObjectOutputStream(out)) {
				objects.writeObject(entry);
			}
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	void remove(Object key) {
		memory.invalidate(key);
		try {
			Files.deleteIfExists(file(key));
		} catch (IOException e) {
			debug.log(Level.WARNING, "Failed to remove cache entry", e);
		}
	}

	void clear() {
		memory.invalidateAll();
		deleteFiles(Long.MAX_VALUE);
	}

	/**
	 * Delete cache files that have not been written for the given time (cleanup of entries that nobody asks for anymore).
	 */
	void purge(Duration olderThan) {
		deleteFiles(System.currentTimeMillis() - olderThan.toMillis());
	}

	private void deleteFiles(long modifiedBefore) {
		if (!Files.isDirectory(folder)) {
			return;
		}

		try (DirectoryStream<Path> files = Files.newDirectoryStream(folder)) {
			for (Path file : files) {
				try {
					if (Files.isRegularFile(file) && (modifiedBefore == Long.MAX_VALUE || Files.getLastModifiedTime(file).toMillis() < modifiedBefore)) {
						Files.deleteIfExists(file);
					}
				} catch (IOException e) {
					debug.log(Level.WARNING, "Failed to delete cache file: " + file, e);
				}
			}
		} catch (IOException e) {
			debug.log(Level.WARNING, "Failed to list cache folder: " + folder, e);
		}
	}

	private boolean isExpired(Entry entry) {
		long timeToLive = entry.getTimeToLiveMillis() > 0 ? entry.getTimeToLiveMillis() : defaultTimeToLive.toMillis();
		return System.currentTimeMillis() - entry.getLatestOfCreationAndUpdateTime() > timeToLive;
	}

	private Entry read(Object key) {
		Path file;
		try {
			file = file(key);
		} catch (IOException e) {
			return null; // not a storable key
		}
		if (!Files.isRegularFile(file)) {
			return null;
		}

		try (InputStream in = Files.newInputStream(file); ObjectInputStream objects = new ObjectInputStream(in)) {
			objects.setObjectInputFilter(FILTER);
			Entry entry = (Entry) objects.readObject();
			if (Objects.equals(entry.getKey(), key)) {
				return entry;
			}
		} catch (Exception | LinkageError e) {
			// e.g. written by an incompatible version of a class: replace it
			debug.warning(String.format("Cache read: %s => %s", key, e));
		}

		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			debug.log(Level.WARNING, "Failed to remove cache entry", e);
		}
		return null;
	}

	private Path file(Object key) throws IOException {
		return folder.resolve(hash(key));
	}

	private static String hash(Object key) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			if (key instanceof CharSequence || key instanceof Number || key instanceof Boolean || key instanceof Character || key instanceof Enum) {
				digest.update((key.getClass().getName() + ":" + key).getBytes(StandardCharsets.UTF_8));
			} else {
				// any other key is identified by its serialized form (keys must be serializable to be stored on disk)
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				try (ObjectOutputStream objects = new ObjectOutputStream(bytes)) {
					objects.writeObject(key);
				}
				digest.update(bytes.toByteArray());
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
