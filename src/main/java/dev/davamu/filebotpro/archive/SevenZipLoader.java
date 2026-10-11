package dev.davamu.filebotpro.archive;

import static dev.davamu.filebotpro.Logging.*;

import java.io.File;

import net.sf.sevenzipjbinding.IArchiveOpenCallback;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.IInStream;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;

import dev.davamu.filebotpro.ApplicationFolder;

public class SevenZipLoader {

	private static boolean nativeLibrariesLoaded = false;

	public static synchronized void requireNativeLibraries() throws SevenZipNativeInitializationException {
		if (nativeLibrariesLoaded) {
			return;
		}

		// prefer native libraries shipped with the application (java.library.path)
		try {
			System.loadLibrary("7-Zip-JBinding");
			SevenZip.initLoadedLibraries(); // NATIVE LIBS MUST BE LOADED WITH SYSTEM CLASSLOADER
			nativeLibrariesLoaded = true;
			return;
		} catch (Throwable e) {
			debug.finest(e::toString);
		}

		// fall back to the native libraries bundled in sevenzipjbinding-all-platforms.jar
		try {
			File folder = ApplicationFolder.Cache.resolve("7-Zip-JBinding"); // user private folder, not the shared temp folder
			folder.mkdirs();
			SevenZip.initSevenZipFromPlatformJAR(folder);
			nativeLibrariesLoaded = true;
		} catch (Throwable e) {
			throw new SevenZipNativeInitializationException("Failed to load 7z-JBinding: " + e.getMessage(), e);
		}
	}

	public static String getNativeVersion() throws SevenZipNativeInitializationException {
		requireNativeLibraries();

		return SevenZip.getSevenZipVersion().version;
	}

	public static IInArchive open(IInStream stream, IArchiveOpenCallback callback) throws SevenZipException, SevenZipNativeInitializationException {
		requireNativeLibraries();

		return (callback == null) ? SevenZip.openInArchive(null, stream) : SevenZip.openInArchive(null, stream, callback);
	}

}
