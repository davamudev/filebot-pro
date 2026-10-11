package dev.davamu.filebotpro;

import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.Settings.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;

public enum ApplicationFolder {

	UserHome(System.getProperty("user.home")),

	AppData(System.getProperty("application.dir", getDefaultApplicationFolder())),

	TemporaryFiles(System.getProperty("java.io.tmpdir")),

	Cache(System.getProperty("application.cache", AppData.resolve("cache").getPath()));

	private File path;

	/**
	 * Platform specific user data folder (e.g. %APPDATA%\FileBot Pro on Windows)
	 */
	private static String getDefaultApplicationFolder() {
		String home = System.getProperty("user.home");
		String os = System.getProperty("os.name", "").toLowerCase();

		if (os.startsWith("windows")) {
			String appdata = System.getenv("APPDATA");
			return Paths.get(appdata != null ? appdata : home, getApplicationName()).toString();
		}
		if (os.startsWith("mac")) {
			return Paths.get(home, "Library", "Application Support", getApplicationName()).toString();
		}
		return Paths.get(home, "." + getApplicationId()).toString();
	}

	ApplicationFolder(String path) {
		try {
			// use canonical file path
			Path f = Paths.get(path);

			// create folders if necessary
			if (!Files.exists(f, LinkOption.NOFOLLOW_LINKS)) {
				Files.createDirectories(f);
			}

			this.path = f.toRealPath(LinkOption.NOFOLLOW_LINKS).toFile();
		} catch (IOException e) {
			debug.log(Level.WARNING, e, e::toString);

			// default to file path as is
			this.path = new File(path).getAbsoluteFile();
		}
	}

	public File get() {
		return path;
	}

	public File resolve(String name) {
		return new File(path, name);
	}

}
