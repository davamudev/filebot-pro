package dev.davamu.filebotpro.media;

import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.hash.VerificationUtilities.*;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import dev.davamu.filebotpro.MetaAttributeView;
import dev.davamu.filebotpro.hash.HashType;

public enum XattrChecksum {

	CRC32;

	// checksum and the file state it was computed from (a different file may be moved to the same path, or the file may be modified in place)
	private record Entry(String value, String stamp) {
	}

	private final Cache<File, Entry> cache = Caffeine.newBuilder().expireAfterAccess(24, TimeUnit.HOURS).build();

	public String computeIfAbsent(File file) {
		String stamp = getStamp(file);

		Entry entry = cache.getIfPresent(file);
		if (entry != null && entry.stamp().equals(stamp)) {
			return entry.value();
		}

		try {
			String value = compute(file);
			cache.put(file, new Entry(value, stamp));
			return value;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public String compute(File file) throws IOException, InterruptedException {
		if (!useExtendedFileAttributes) {
			return computeHash(file, getHashType());
		}

		// read xattr
		MetaAttributeView xattr = new MetaAttributeView(file);

		String value = xattr.get(getKey());
		if (value != null) {
			// checksums stored by older versions have no stamp
			String stamp = xattr.get(getStampKey());
			if (stamp == null || stamp.equals(getStamp(file))) {
				return value;
			}
		}

		// compute checksum
		value = computeHash(file, getHashType());

		// store checksum (and make sure Last-Modified date is not changed)
		long t = file.lastModified();
		String stamp = getStamp(file);
		try {
			xattr.put(getKey(), value); // may or may not change Last-Modified date
			xattr.put(getStampKey(), stamp);
		} catch (Exception e) {
			debug.warning(cause("Failed to set xattr", e));
		} finally {
			new File(file.getPath()).setLastModified(t); // file may be a read-only FastFile
		}

		return value;
	}

	public void invalidate(File file) {
		cache.invalidate(file);
	}

	public void clear(File file) {
		cache.invalidate(file);

		if (useExtendedFileAttributes) {
			try {
				MetaAttributeView xattr = new MetaAttributeView(file);
				xattr.put(getKey(), null);
				xattr.put(getStampKey(), null);
			} catch (Exception e) {
				debug.warning(cause("Failed to set xattr", e));
			}
		}
	}

	private String getKey() {
		return name();
	}

	private String getStampKey() {
		return name() + ".stamp";
	}

	private String getStamp(File file) {
		return file.lastModified() + ":" + file.length();
	}

	private HashType getHashType() {
		switch (this) {
		case CRC32:
			return HashType.SFV;
		}
		return null;
	}

	private static final boolean useExtendedFileAttributes = useExtendedFileAttributes();

}
