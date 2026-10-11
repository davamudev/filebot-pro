package dev.davamu.filebotpro.mediainfo;

import java.io.File;

import dev.davamu.filebotpro.ApplicationFolder;
import dev.davamu.filebotpro.NativeLibraries;
import com.sun.jna.Platform;

public class MediaInfoException extends RuntimeException {

	public MediaInfoException(String message) {
		super(message);
	}

	public MediaInfoException(LinkageError e) {
		super(getLinkageErrorMessage(e), e);
	}

	private static String getLinkageErrorMessage(LinkageError e) {
		String name = Platform.isWindows() ? "MediaInfo.dll" : Platform.isMac() ? "libmediainfo.dylib" : "libmediainfo.so";
		String arch = System.getProperty("os.arch");
		String bit = Platform.is64Bit() ? "64-bit" : "32-bit";

		// JNA error messages list the entire class path, so we only keep the first line
		String cause = String.valueOf(e.getMessage()).lines().findFirst().orElse("");
		File folder = ApplicationFolder.AppData.resolve(NativeLibraries.FOLDER_NAME);

		String install = Platform.isLinux() ? "Install the libmediainfo package (e.g. sudo apt install libmediainfo0v5)" : "Install MediaInfo";

		return String.format("Unable to load %s (%s) native library %s: %s %s or copy %s into %s", arch, bit, name, cause, install, name, folder);
	}

}
