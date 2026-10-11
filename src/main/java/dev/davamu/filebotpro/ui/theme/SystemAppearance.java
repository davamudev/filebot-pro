package dev.davamu.filebotpro.ui.theme;

import static dev.davamu.filebotpro.Logging.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

/**
 * Detects whether the operating system is currently using a dark appearance.
 */
final class SystemAppearance {

	private static final String WINDOWS_PERSONALIZE_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";

	static boolean isDark() {
		try {
			if (Platform.isWindows()) {
				return Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, WINDOWS_PERSONALIZE_KEY, "AppsUseLightTheme") && Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, WINDOWS_PERSONALIZE_KEY, "AppsUseLightTheme") == 0;
			}
			if (Platform.isMac()) {
				return exec("defaults", "read", "-g", "AppleInterfaceStyle").equalsIgnoreCase("dark");
			}

			// GNOME 42+ (and most GTK based desktops)
			String scheme = exec("gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
			if (scheme.contains("dark")) {
				return true;
			}
			return exec("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme").toLowerCase().contains("dark");
		} catch (Throwable e) {
			debug.log(Level.FINEST, e, e::toString);
		}
		return false;
	}

	/**
	 * Polling is cheap on Windows (registry lookup) but spawns a process on other platforms.
	 */
	static int getPollingInterval() {
		return Platform.isWindows() ? 2000 : 10000;
	}

	private static String exec(String... command) throws Exception {
		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			String line = reader.readLine();
			process.waitFor(2, TimeUnit.SECONDS);
			return line == null ? "" : line.trim();
		} finally {
			process.destroy();
		}
	}

	private SystemAppearance() {
		throw new UnsupportedOperationException();
	}

}
