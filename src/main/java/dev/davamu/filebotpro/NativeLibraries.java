package dev.davamu.filebotpro;

import static dev.davamu.filebotpro.Logging.*;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.sun.jna.Platform;

/**
 * Locates optional native libraries (MediaInfo, Chromaprint fpcalc, ffprobe, ...) so they can simply be dropped into a {@code native} folder next to the application or in the user data folder.
 */
public final class NativeLibraries {

	public static final String FOLDER_NAME = "native";

	private static boolean configured = false;

	public static synchronized void configure() {
		if (configured) {
			return;
		}
		configured = true;

		List<File> folders = getSearchFolders();

		// JNA libraries (e.g. MediaInfo.dll, libmediainfo.so, libmediainfo.dylib)
		if (!folders.isEmpty()) {
			Set<String> path = new LinkedHashSet<String>();
			String jnaLibraryPath = System.getProperty("jna.library.path");
			if (jnaLibraryPath != null && !jnaLibraryPath.isEmpty()) {
				path.add(jnaLibraryPath);
			}
			folders.forEach(f -> path.add(f.getPath()));
			System.setProperty("jna.library.path", String.join(File.pathSeparator, path));
		}

		// command-line tools
		setExecutableProperty("dev.davamu.filebotpro.AcoustID.fpcalc", "fpcalc", folders);
		setExecutableProperty("dev.davamu.filebotpro.media.ffprobe", "ffprobe", folders);
		setExecutableProperty("dev.davamu.filebotpro.archive.7z", "7z", folders);
		setExecutableProperty("dev.davamu.filebotpro.archive.unrar", "unrar", folders);
		setExecutableProperty("dev.davamu.filebotpro.media.mkvpropedit", "mkvpropedit", folders);
		setExecutableProperty("dev.davamu.filebotpro.media.AtomicParsley", "AtomicParsley", folders);

		debug.fine(() -> "Native search path: " + folders);
	}

	public static List<File> getSearchFolders() {
		List<File> folders = new ArrayList<File>();

		// development builds (set by gradlew run)
		String folder = System.getProperty("dev.davamu.filebotpro.native.dir");
		if (folder != null && !folder.isEmpty()) {
			folders.add(new File(folder));
		}

		// native folder next to the application jar (jpackage: app/native, installDist: <home>/native)
		try {
			File jar = new File(NativeLibraries.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			if (jar.isFile()) {
				folders.add(new File(jar.getParentFile(), FOLDER_NAME));
				folders.add(new File(jar.getParentFile().getParentFile(), FOLDER_NAME));
			}
		} catch (Exception e) {
			debug.finest(e::toString);
		}

		// <application>/native
		String home = System.getProperty("application.home");
		if (home != null && !home.isEmpty()) {
			folders.add(new File(home, FOLDER_NAME));
		}

		// jpackage application image
		String launcher = System.getProperty("jpackage.app-path");
		if (launcher != null && !launcher.isEmpty()) {
			File parent = new File(launcher).getParentFile();
			folders.add(new File(parent, FOLDER_NAME));
			folders.add(new File(parent, "app/" + FOLDER_NAME));
		}

		// user data folder (no admin rights required)
		folders.add(ApplicationFolder.AppData.resolve(FOLDER_NAME));

		// well-known installation folders
		if (Platform.isWindows()) {
			String programFiles = System.getenv("ProgramFiles");
			if (programFiles != null) {
				folders.add(new File(programFiles, "MediaInfo"));
				folders.add(new File(programFiles, "7-Zip"));
			}
		} else if (Platform.isMac()) {
			folders.add(new File("/opt/homebrew/lib"));
			folders.add(new File("/opt/homebrew/bin"));
			folders.add(new File("/usr/local/lib"));
			folders.add(new File("/usr/local/bin"));
		}

		folders.removeIf(f -> !f.isDirectory());
		return folders;
	}

	private static void setExecutableProperty(String property, String name, List<File> folders) {
		if (System.getProperty(property) != null) {
			return;
		}

		for (File folder : folders) {
			File executable = new File(folder, Platform.isWindows() ? name + ".exe" : name);
			if (executable.isFile() && executable.canExecute()) {
				System.setProperty(property, executable.getPath());
				return;
			}
		}

		// resolve against the PATH (e.g. ffprobe.exe, or wrapper scripts like ffprobe.cmd which are not found by default on Windows)
		File executable = Execute.which(name);
		if (executable != null) {
			System.setProperty(property, executable.getPath());
		}
	}

	private NativeLibraries() {
		throw new UnsupportedOperationException();
	}

}
