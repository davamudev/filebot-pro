package dev.davamu.filebotpro;

import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.sun.jna.Platform;

import dev.davamu.filebotpro.UserFiles.FileChooser;
import dev.davamu.filebotpro.cli.ArgumentBean;
import dev.davamu.filebotpro.util.PreferencesList;
import dev.davamu.filebotpro.util.PreferencesMap;
import dev.davamu.filebotpro.util.PreferencesMap.JsonAdapter;
import dev.davamu.filebotpro.util.PreferencesMap.PreferencesEntry;
import dev.davamu.filebotpro.util.PreferencesMap.StringAdapter;
import dev.davamu.filebotpro.util.SystemProperty;

public final class Settings {

	public static String getApplicationName() {
		return getApplicationProperty("application.name");
	}

	public static String getApplicationVersion() {
		return getApplicationProperty("application.version");
	}

	public static int getApplicationRevisionNumber() {
		try {
			return Integer.parseInt(getApplicationProperty("application.revision"));
		} catch (Exception e) {
			return 0;
		}
	}

	/**
	 * @return short git commit hash of this build, or null if unknown
	 */
	public static String getApplicationCommit() {
		try {
			String commit = getApplicationProperty("application.commit").trim();
			return commit.matches("\\p{XDigit}+") ? commit : null;
		} catch (Exception e) {
			return null;
		}
	}

	public static String getApplicationProperty(String key) {
		return ResourceBundle.getBundle(Settings.class.getName(), Locale.ROOT).getString(key);
	}

	public static String getApplicationId() {
		return getApplicationProperty("application.id");
	}

	/**
	 * API keys are looked up in this order: -Dapikey.NAME=KEY, the FILEBOT_PRO_APIKEY_NAME environment variable, api-keys.properties in the user data folder, and finally the keys embedded at build time.
	 */
	public static String getApiKey(String name) {
		String key = System.getProperty("apikey." + name);
		if (key == null || key.isEmpty()) {
			key = System.getenv("FILEBOT_PRO_APIKEY_" + name.toUpperCase(Locale.ROOT).replace('.', '_'));
		}
		if (key == null || key.isEmpty()) {
			key = getUserApiKeys().getProperty(name);
		}
		if (key == null || key.isEmpty()) {
			// personal keys (e.g. omdb, thetvdb) are never embedded and have no entry in Settings.properties
			ResourceBundle bundle = ResourceBundle.getBundle(Settings.class.getName(), Locale.ROOT);
			key = bundle.containsKey("apikey." + name) ? bundle.getString("apikey." + name) : null;
		}
		return key == null ? null : key.trim();
	}

	public static File getUserApiKeysFile() {
		return ApplicationFolder.AppData.resolve("api-keys.properties");
	}

	private static Properties userApiKeys;

	private static synchronized Properties getUserApiKeys() {
		if (userApiKeys == null) {
			userApiKeys = new Properties();
			File file = getUserApiKeysFile();
			if (file.isFile()) {
				try {
					// ignore the byte order mark that some Windows editors write
					String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("﻿", "");
					userApiKeys.load(new StringReader(text));
				} catch (Exception e) {
					log.warning(cause("Failed to read " + file, e));
				}
			}
		}
		return userApiKeys;
	}

	public static boolean isUnixFS() {
		return Boolean.parseBoolean(System.getProperty("unixfs"));
	}

	public static boolean useNativeShell() {
		return Boolean.parseBoolean(System.getProperty("useNativeShell"));
	}

	public static boolean useGVFS() {
		return Boolean.parseBoolean(System.getProperty("useGVFS"));
	}

	public static boolean useMediaParser() {
		return !"false".equalsIgnoreCase(System.getProperty("useMediaParser"));
	}

	public static boolean useLocalIndex() {
		return !"false".equalsIgnoreCase(System.getProperty("useLocalIndex"));
	}

	public static boolean useExtendedFileAttributes() {
		return Boolean.parseBoolean(System.getProperty("useExtendedFileAttributes"));
	}

	public static boolean useCreationDate() {
		return Boolean.parseBoolean(System.getProperty("useCreationDate"));
	}

	public static boolean useRenameHistory() {
		return Boolean.parseBoolean(System.getProperty("application.rename.history", "true"));
	}

	public static String getApplicationDeployment() {
		return System.getProperty("application.deployment", "jar");
	}

	public static boolean isWindowsApp() {
		return isApplicationDeployment("msi", "zip");
	}

	public static boolean isUbuntuApp() {
		return isApplicationDeployment("snap");
	}

	public static boolean isMacApp() {
		return isApplicationDeployment("app", "pkg");
	}

	public static boolean isAutoUpdateEnabled() {
		return isApplicationDeployment("snap");
	}

	private static boolean isApplicationDeployment(String... ids) {
		String current = getApplicationDeployment();
		for (String id : ids) {
			if (current != null && current.equals(id))
				return true;
		}
		return false;
	}

	public static FileChooser getPreferredFileChooser() {
		// native dialogs by default: Explorer dialog on Windows, NSOpenPanel / NSSavePanel on macOS, zenity / kdialog on Linux (Swing if neither is installed)
		String name = System.getProperty("dev.davamu.filebotpro.UserFiles.fileChooser", Platform.isWindows() ? "Windows" : Platform.isMac() ? "COCOA" : "Linux");
		for (FileChooser it : FileChooser.values()) {
			if (it.name().equalsIgnoreCase(name)) {
				return it;
			}
		}
		return FileChooser.Swing;
	}

	public static int getPreferredThreadPoolSize() {
		return SystemProperty.of("threadPool", Integer::parseInt, Runtime.getRuntime().availableProcessors()).get();
	}

	public static int getPreferredParallelism() {
		return SystemProperty.of("parallelism", Integer::parseInt, 1).get();
	}

	public static String getEmbeddedHelpURL() {
		// add #hash so we can dynamically adjust the slides for the various platforms via JavaScript
		return getApplicationProperty("link.app.help") + '#' + getApplicationDeployment();
	}

	public static String getWindowTitle() {
		return isAutoUpdateEnabled() ? getApplicationName() : String.format("%s %s", getApplicationName(), getApplicationVersion());
	}

	public static String getApplicationIdentifier() {
		return String.format("%s %s (r%d)", getApplicationName(), getApplicationVersion(), getApplicationRevisionNumber());
	}

	public static String getJavaRuntimeIdentifier() {
		return String.format("%s %s", System.getProperty("java.runtime.name"), System.getProperty("java.version"));
	}

	public static String getSystemIdentifier() {
		return String.format("%s %s (%s)", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
	}

	private static ArgumentBean applicationArguments;

	public static void setApplicationArguments(ArgumentBean args) {
		applicationArguments = args;
	}

	public static ArgumentBean getApplicationArguments() {
		return applicationArguments;
	}

	public static Settings forPackage(Class<?> type) {
		return new Settings(Preferences.userNodeForPackage(type));
	}

	private final Preferences prefs;

	private Settings(Preferences prefs) {
		this.prefs = prefs;
	}

	public Settings node(String nodeName) {
		return new Settings(prefs.node(nodeName));
	}

	public String get(String key) {
		return get(key, null);
	}

	public String get(String key, String def) {
		return prefs.get(key, def);
	}

	public void put(String key, String value) {
		if (value != null) {
			prefs.put(key, value);
		} else {
			remove(key);
		}
	}

	public void remove(String key) {
		prefs.remove(key);
	}

	public PreferencesEntry<String> entry(String key) {
		return new PreferencesEntry<String>(prefs, key, new StringAdapter());
	}

	public PreferencesMap<String> asMap() {
		return PreferencesMap.map(prefs);
	}

	public <T> PreferencesMap<T> asMap(Class<T> cls) {
		return PreferencesMap.map(prefs, new JsonAdapter(cls));
	}

	public PreferencesList<String> asList() {
		return PreferencesList.map(prefs);
	}

	public <T> PreferencesList<T> asList(Class<T> cls) {
		return PreferencesList.map(prefs, new JsonAdapter(cls));
	}

	public void clear() {
		try {
			// remove child nodes
			for (String nodeName : prefs.childrenNames()) {
				prefs.node(nodeName).removeNode();
			}

			// remove entries
			prefs.clear();
		} catch (BackingStoreException e) {
			debug.warning(e.getMessage());
		}
	}

	public static void store(File f) {
		try (OutputStream out = new BufferedOutputStream(new FileOutputStream(f))) {
			// application preferences only, not the preferences of every other Java application of this user
			Preferences.userNodeForPackage(Settings.class).exportSubtree(out);
		} catch (Exception e) {
			debug.log(Level.SEVERE, e, e::toString);
		}
	}

	public static void restore(File f) {
		try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
			Preferences.importPreferences(in);
		} catch (Exception e) {
			debug.log(Level.SEVERE, e, e::toString);
		}
	}

}
