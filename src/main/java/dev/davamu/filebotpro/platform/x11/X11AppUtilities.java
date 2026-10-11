package dev.davamu.filebotpro.platform.x11;

import static dev.davamu.filebotpro.Logging.*;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.sun.jna.Platform;

public class X11AppUtilities {

	/**
	 * Set the X11 WM_CLASS of all windows, so docks and taskbars (GNOME, KDE, ...) match them with the
	 * StartupWMClass of the installed .desktop file and show the application icon. Needs
	 * {@code --add-opens java.desktop/sun.awt.X11=ALL-UNNAMED} and must be called before the first window is created.
	 */
	public static void setWindowClass(String name) {
		if (GraphicsEnvironment.isHeadless()) {
			return;
		}

		try {
			Toolkit toolkit = Toolkit.getDefaultToolkit();
			if (toolkit.getClass().getName().equals("sun.awt.X11.XToolkit")) {
				Field field = toolkit.getClass().getDeclaredField("awtAppClassName");
				field.setAccessible(true);
				field.set(null, name);
			}
		} catch (Throwable t) {
			debug.log(Level.FINE, "Failed to set WM_CLASS: " + t);
		}
	}

	private static final String NONREPARENTING = "_JAVA_AWT_WM_NONREPARENTING";

	// tiling window managers that do not reparent windows (Java shows blank grey windows unless it knows)
	private static final Pattern TILING_WINDOW_MANAGERS = Pattern.compile("i3|sway|hyprland|bspwm|dwm|awesome|qtile|xmonad|river|niri|herbstluftwm|spectrwm|leftwm|wayfire", Pattern.CASE_INSENSITIVE);

	/**
	 * @return true if the desktop session is a non-reparenting (i.e. tiling) window manager like i3 or sway
	 */
	public static boolean isNonReparentingWindowManager() {
		if (Stream.of("I3SOCK", "SWAYSOCK", "HYPRLAND_INSTANCE_SIGNATURE").anyMatch(k -> System.getenv(k) != null)) {
			return true;
		}
		return Stream.of("XDG_CURRENT_DESKTOP", "XDG_SESSION_DESKTOP", "DESKTOP_SESSION").map(System::getenv).filter(v -> v != null).flatMap(v -> Stream.of(v.split("[:;/]"))).anyMatch(v -> TILING_WINDOW_MANAGERS.matcher(v.trim().toLowerCase(Locale.ROOT)).matches());
	}

	/**
	 * Java needs {@code _JAVA_AWT_WM_NONREPARENTING=1} on tiling window managers, but only reads it from the environment when the JVM starts,
	 * so start the application again with that environment variable, and then exit with the exit code of the new process. Must be called
	 * before AWT is initialized.
	 */
	public static void relaunchForNonReparentingWindowManager() {
		if (!Platform.isX11() || Platform.isMac() || System.getenv(NONREPARENTING) != null || !isNonReparentingWindowManager()) {
			return;
		}

		ProcessHandle.Info info = ProcessHandle.current().info();
		Optional<String> command = info.command();
		if (command.isEmpty()) {
			return;
		}

		List<String> commandLine = new ArrayList<String>();
		commandLine.add(command.get());
		commandLine.addAll(List.of(info.arguments().orElse(new String[0])));

		try {
			debug.fine(() -> "Restart with " + NONREPARENTING + "=1: " + commandLine);
			ProcessBuilder process = new ProcessBuilder(commandLine).inheritIO();
			process.environment().put(NONREPARENTING, "1");
			System.exit(process.start().waitFor());
		} catch (Exception e) {
			debug.log(Level.WARNING, "Failed to restart with " + NONREPARENTING + "=1", e);
		}
	}

}
