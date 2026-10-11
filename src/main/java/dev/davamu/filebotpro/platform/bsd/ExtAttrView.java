package dev.davamu.filebotpro.platform.bsd;

import static java.nio.charset.StandardCharsets.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.Execute.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.util.RegularExpressions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

import dev.davamu.filebotpro.util.XattrView;

/**
 * Extended attributes on FreeBSD and NetBSD via the extattr(2) system calls (e.g. UFS, ZFS), or the lsextattr / getextattr / setextattr /
 * rmextattr commands if the system calls are not available
 */
public class ExtAttrView implements XattrView {

	private final String path;

	public ExtAttrView(Path path) {
		this.path = path.toString();
	}

	@Override
	public List<String> list() throws IOException {
		LibC lib = LibC.get();
		if (lib == null) {
			return SPACE.splitAsStream(execute("lsextattr", "-q", "user", path)).map(String::trim).filter(s -> s.length() > 0).collect(toList());
		}

		long size = lib.extattr_list_file(path, EXTATTR_NAMESPACE_USER, null, new NativeLong(0)).longValue();
		if (size < 0) {
			throw new IOException("extattr_list_file failed: " + Native.getLastError() + ": " + path);
		}

		List<String> names = new ArrayList<String>();
		if (size == 0) {
			return names;
		}

		Memory data = new Memory(size);
		size = lib.extattr_list_file(path, EXTATTR_NAMESPACE_USER, data, new NativeLong(size)).longValue();
		if (size < 0) {
			throw new IOException("extattr_list_file failed: " + Native.getLastError() + ": " + path);
		}

		// each name is prefixed with its length (one byte) and not null-terminated
		byte[] bytes = data.getByteArray(0, (int) size);
		for (int i = 0; i < bytes.length;) {
			int length = bytes[i] & 0xFF;
			names.add(new String(bytes, i + 1, Math.min(length, bytes.length - i - 1), UTF_8));
			i += 1 + length;
		}
		return names;
	}

	@Override
	public String read(String key) {
		LibC lib = LibC.get();
		if (lib == null) {
			try {
				return execute("getextattr", "-q", "user", key, path).toString().trim();
			} catch (IOException e) {
				return null;
			}
		}

		long size = lib.extattr_get_file(path, EXTATTR_NAMESPACE_USER, key, null, new NativeLong(0)).longValue();
		if (size < 0) {
			return null; // e.g. ENOATTR
		}
		if (size == 0) {
			return "";
		}

		Memory data = new Memory(size);
		size = lib.extattr_get_file(path, EXTATTR_NAMESPACE_USER, key, data, new NativeLong(size)).longValue();
		return size < 0 ? null : new String(data.getByteArray(0, (int) size), UTF_8);
	}

	@Override
	public void write(String key, String value) throws IOException {
		LibC lib = LibC.get();
		if (lib == null) {
			execute("setextattr", "-q", "user", key, value, path);
			return;
		}

		byte[] bytes = value.getBytes(UTF_8);
		Memory data = new Memory(Math.max(1, bytes.length));
		data.write(0, bytes, 0, bytes.length);

		if (lib.extattr_set_file(path, EXTATTR_NAMESPACE_USER, key, data, new NativeLong(bytes.length)) < 0) {
			throw new IOException("extattr_set_file failed: " + Native.getLastError() + ": " + path);
		}
	}

	@Override
	public void delete(String key) throws IOException {
		LibC lib = LibC.get();
		if (lib == null) {
			execute("rmextattr", "-q", "user", key, path);
			return;
		}

		if (lib.extattr_delete_file(path, EXTATTR_NAMESPACE_USER, key) < 0) {
			throw new IOException("extattr_delete_file failed: " + Native.getLastError() + ": " + path);
		}
	}

	// see /usr/include/sys/extattr.h
	private static final int EXTATTR_NAMESPACE_USER = 1;

	private interface LibC extends Library {

		// see https://man.freebsd.org/cgi/man.cgi?query=extattr_get_file&sektion=2
		NativeLong extattr_get_file(String path, int attrnamespace, String attrname, Pointer data, NativeLong nbytes);

		// ssize_t on FreeBSD and int on NetBSD (only -1 or not)
		int extattr_set_file(String path, int attrnamespace, String attrname, Pointer data, NativeLong nbytes);

		int extattr_delete_file(String path, int attrnamespace, String attrname);

		NativeLong extattr_list_file(String path, int attrnamespace, Pointer data, NativeLong nbytes);

		static LibC get() {
			return Holder.INSTANCE;
		}
	}

	private static class Holder {

		private static final LibC INSTANCE = load();

		private static LibC load() {
			try {
				LibC lib = Native.load("c", LibC.class);
				lib.extattr_list_file("/", EXTATTR_NAMESPACE_USER, null, new NativeLong(0)); // make sure the system calls exist
				return lib;
			} catch (Throwable e) {
				debug.fine(() -> "extattr system calls not available: " + e);
				return null;
			}
		}
	}

}
