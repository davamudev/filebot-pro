package dev.davamu.filebotpro.util;

import static dev.davamu.filebotpro.Logging.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Platform;

/**
 * Copy-on-write clones (i.e. the clone shares data blocks with the original until either file is modified) via clonefile(2) on macOS / APFS
 * and ioctl(FICLONE) on Linux / Btrfs, XFS, bcachefs. On Windows, CopyFileEx (i.e. Files.copy) uses block cloning on ReFS / Dev Drive by
 * itself (Windows 11 24H2 and later).
 */
public final class CopyOnWrite {

	/**
	 * Clone the given file or folder, or copy it if the file system does not support copy-on-write clones
	 */
	public static File cloneOrCopy(File source, File destination) throws IOException {
		if (Platform.isWindows() || !isSupported()) {
			return FileUtilities.copyAs(source, destination);
		}

		destination = FileUtilities.resolveDestination(source, destination);

		if (source.isDirectory()) {
			Path from = source.toPath();
			Path to = destination.toPath();

			Files.walkFileTree(from, new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					Files.createDirectories(to.resolve(from.relativize(dir)));
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					cloneOrCopyFile(file, to.resolve(from.relativize(file)));
					return FileVisitResult.CONTINUE;
				}
			});
			return destination;
		}

		cloneOrCopyFile(source.toPath(), destination.toPath());
		return destination;
	}

	private static void cloneOrCopyFile(Path source, Path destination) throws IOException {
		try {
			cloneFile(source, destination);
		} catch (FileSystemException e) {
			debug.fine(cause("Copy-on-write clone not supported", e));
			Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * Clone the given file, and fail if the file system does not support copy-on-write clones (e.g. different file systems)
	 */
	public static void cloneFile(Path source, Path destination) throws IOException {
		if (Platform.isMac()) {
			// clonefile(2) does not overwrite existing files
			Files.deleteIfExists(destination);

			if (LibC.INSTANCE.clonefile(source.toString(), destination.toString(), 0) != 0) {
				throw new FileSystemException(source.toString(), destination.toString(), "clonefile failed (errno " + Native.getLastError() + ")");
			}
			return;
		}

		if (Platform.isLinux()) {
			int src = LibC.INSTANCE.open(source.toString(), O_RDONLY);
			if (src < 0) {
				throw new FileSystemException(source.toString(), null, "open failed (errno " + Native.getLastError() + ")");
			}

			try {
				int dst = LibC.INSTANCE.open(destination.toString(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
				if (dst < 0) {
					throw new FileSystemException(destination.toString(), null, "open failed (errno " + Native.getLastError() + ")");
				}

				int result;
				int errno;
				try {
					result = LibC.INSTANCE.ioctl(dst, new NativeLong(FICLONE), src);
					errno = Native.getLastError();
				} finally {
					LibC.INSTANCE.close(dst);
				}

				if (result != 0) {
					Files.deleteIfExists(destination);
					throw new FileSystemException(source.toString(), destination.toString(), "ioctl FICLONE failed (errno " + errno + ")");
				}
			} finally {
				LibC.INSTANCE.close(src);
			}

			// same permissions as the original file
			try {
				Files.setPosixFilePermissions(destination, Files.getPosixFilePermissions(source));
			} catch (Exception e) {
				debug.finest(e::toString);
			}
			return;
		}

		throw new FileSystemException(source.toString(), destination.toString(), "Copy-on-write clones are not supported on this platform");
	}

	/**
	 * @return true if both paths are on the same device (e.g. same partition or Btrfs subvolume)
	 */
	public static boolean isSameDevice(Path a, Path b) {
		try {
			if (Platform.isWindows()) {
				return Files.getFileStore(a).equals(Files.getFileStore(b));
			}
			return Files.getAttribute(a, "unix:dev", LinkOption.NOFOLLOW_LINKS).equals(Files.getAttribute(b, "unix:dev", LinkOption.NOFOLLOW_LINKS));
		} catch (Exception e) {
			debug.finest(e::toString);
			return true; // assume the same device if we cannot tell
		}
	}

	public static boolean isSupported() {
		return Platform.isMac() || Platform.isLinux();
	}

	// see /usr/include/linux/fs.h and /usr/include/asm-generic/fcntl.h
	private static final int FICLONE = 0x40049409;
	private static final int O_RDONLY = 00;
	private static final int O_WRONLY = 01;
	private static final int O_CREAT = 0100;
	private static final int O_TRUNC = 01000;

	private interface LibC extends Library {

		LibC INSTANCE = Native.load("c", LibC.class);

		// see https://www.man7.org/linux/man-pages/man2/open.2.html
		int open(String path, int flags, Object... mode);

		int close(int fd);

		// see https://www.man7.org/linux/man-pages/man2/ioctl_ficlone.2.html
		int ioctl(int fd, NativeLong request, Object... args);

		// see https://keith.github.io/xcode-man-pages/clonefile.2.html
		int clonefile(String src, String dst, int flags);
	}

	private CopyOnWrite() {
		throw new UnsupportedOperationException();
	}

}
