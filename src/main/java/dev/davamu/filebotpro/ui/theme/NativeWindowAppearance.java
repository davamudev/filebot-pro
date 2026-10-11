package dev.davamu.filebotpro.ui.theme;

import static dev.davamu.filebotpro.Logging.*;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;

import javax.swing.RootPaneContainer;

import com.formdev.flatlaf.ui.FlatNativeWindowBorder;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * Native window title bars and frames follow the light or dark theme, also when the theme changes at runtime:
 * NSAppearance per window on macOS, and immersive dark mode on Windows for windows without FlatLaf decorations.
 */
final class NativeWindowAppearance {

	private static final String MAC_WINDOW_APPEARANCE = "apple.awt.windowAppearance";

	// DWMWA_USE_IMMERSIVE_DARK_MODE (Windows 10 20H1 and later, 19 before that)
	private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
	private static final int DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1 = 19;

	private interface Dwmapi extends StdCallLibrary {

		Dwmapi INSTANCE = Native.load("dwmapi", Dwmapi.class, W32APIOptions.DEFAULT_OPTIONS);

		int DwmSetWindowAttribute(Pointer hwnd, int attribute, IntByReference value, int size);
	}

	private static boolean dark;

	public static void install(boolean darkMode) {
		dark = darkMode;

		if (!Platform.isMac() && !Platform.isWindows()) {
			return;
		}

		// windows opened later
		try {
			Toolkit.getDefaultToolkit().addAWTEventListener(evt -> {
				if (evt.getID() == WindowEvent.WINDOW_OPENED) {
					update(((WindowEvent) evt).getWindow());
				}
			}, AWTEvent.WINDOW_EVENT_MASK);
		} catch (Throwable e) {
			debug.finest(e::toString);
		}
	}

	public static void setDark(boolean darkMode) {
		dark = darkMode;

		if (Platform.isMac() || Platform.isWindows()) {
			for (Window window : Window.getWindows()) {
				update(window);
			}
		}
	}

	private static void update(Window window) {
		try {
			if (Platform.isMac() && window instanceof RootPaneContainer) {
				((RootPaneContainer) window).getRootPane().putClientProperty(MAC_WINDOW_APPEARANCE, dark ? "NSAppearanceNameDarkAqua" : "NSAppearanceNameAqua");
			}

			if (Platform.isWindows() && window.isDisplayable() && !FlatNativeWindowBorder.hasCustomDecoration(window)) {
				Pointer hwnd = Native.getWindowPointer(window);
				IntByReference value = new IntByReference(dark ? 1 : 0);
				if (Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, value, 4) != 0) {
					Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1, value, 4);
				}
			}
		} catch (Throwable e) {
			debug.finest(e::toString);
		}
	}

	private NativeWindowAppearance() {
		throw new UnsupportedOperationException();
	}

}
