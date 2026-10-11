package dev.davamu.filebotpro.ui.theme;

import static dev.davamu.filebotpro.Logging.*;

import java.awt.Color;
import java.awt.Window;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import dev.davamu.filebotpro.Settings;
import dev.davamu.filebotpro.util.PreferencesMap.PreferencesEntry;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;

import net.miginfocom.swing.SwingComponentWrapper;

/**
 * Installs the FlatLaf based look and feel and switches between light and dark mode at runtime.
 */
public final class ThemeManager {

	/** Brand accent color (Tailwind orange-500) */
	public static final Color ACCENT = new Color(0xF97316);

	private static final PreferencesEntry<String> preference = Settings.forPackage(ThemeManager.class).entry("theme").defaultValue(Theme.System.name());

	private static final List<Runnable> listeners = new CopyOnWriteArrayList<Runnable>();

	private static Theme theme = Theme.System;
	private static boolean dark = false;

	private static ScheduledExecutorService systemAppearanceMonitor;
	private static ScheduledFuture<?> systemAppearancePolling;

	public static void install() {
		try {
			FlatInterFont.install();
			FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY);
			FlatLaf.setPreferredLightFontFamily(FlatInterFont.FAMILY_LIGHT);
			FlatLaf.setPreferredSemiboldFontFamily(FlatInterFont.FAMILY_SEMIBOLD);

			FlatJetBrainsMonoFont.install();
			FlatLaf.setPreferredMonospacedFontFamily(FlatJetBrainsMonoFont.FAMILY);
		} catch (Throwable e) {
			debug.log(Level.WARNING, e, e::toString);
		}

		// load FlatLaf.properties, FlatLightLaf.properties and FlatDarkLaf.properties from this package
		FlatLaf.registerCustomDefaultsSource(ThemeManager.class.getPackageName());

		// MigLayout platform paddings are meant for native look and feels and would clip FlatLaf components by a pixel or two
		SwingComponentWrapper.setVisualPaddingEnabled(false);

		theme = getInitialTheme();
		apply(false);
		updateSystemAppearanceMonitor();

		NativeWindowAppearance.install(dark);
	}

	/**
	 * Native window title bars follow the theme (macOS). Must be called before AWT is initialized, because the property is read only once on startup.
	 */
	public static void configureNativeAppearance() {
		if (System.getProperty("apple.awt.application.appearance") == null) {
			switch (getInitialTheme()) {
			case Dark:
				System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua");
				break;
			case Light:
				System.setProperty("apple.awt.application.appearance", "NSAppearanceNameAqua");
				break;
			default:
				System.setProperty("apple.awt.application.appearance", "system");
				break;
			}
		}
	}

	private static Theme getInitialTheme() {
		String override = System.getProperty("dev.davamu.filebotpro.theme");
		if (override != null) {
			return Theme.forName(override);
		}

		// legacy option
		if (Boolean.parseBoolean(System.getProperty("DarkMode"))) {
			return Theme.Dark;
		}

		return Theme.forName(preference.getValue());
	}

	public static Theme getTheme() {
		return theme;
	}

	public static boolean isDark() {
		return dark;
	}

	public static void setTheme(Theme t) {
		theme = t;
		preference.setValue(t.name());

		apply(true);
		updateSystemAppearanceMonitor();

		// theme may change while appearance stays the same (e.g. Dark -> System)
		listeners.forEach(Runnable::run);
	}

	/**
	 * Cycle through System, Light and Dark.
	 */
	public static Theme next() {
		Theme[] values = Theme.values();
		return values[(theme.ordinal() + 1) % values.length];
	}

	public static void addThemeListener(Runnable listener) {
		listeners.add(listener);
	}

	public static void removeThemeListener(Runnable listener) {
		listeners.remove(listener);
	}

	private static boolean resolveDark() {
		switch (theme) {
		case Dark:
			return true;
		case Light:
			return false;
		default:
			return SystemAppearance.isDark();
		}
	}

	private static void apply(boolean animate) {
		apply(resolveDark(), animate);
	}

	private static void apply(boolean darkMode, boolean animate) {
		boolean installed = UIManager.getLookAndFeel() instanceof FlatLaf;
		if (installed && darkMode == dark) {
			return;
		}

		ColorMigration migration = installed ? ColorMigration.capture(dark) : null;

		dark = darkMode;
		boolean animated = animate && installed && hasVisibleWindow();

		try {
			if (animated) {
				FlatAnimatedLafChange.showSnapshot();
			}

			FlatLaf.setup(dark ? new FlatDarkLaf() : new FlatLightLaf());

			if (installed) {
				FlatLaf.updateUI();
				migration.apply();
				NativeWindowAppearance.setDark(dark);
			}

		} catch (Throwable e) {
			log.log(Level.SEVERE, e, message("Failed to set look and feel", e));
		} finally {
			// always remove the snapshot, otherwise it stays on top of the window and blocks all input
			if (animated) {
				FlatAnimatedLafChange.hideSnapshotWithAnimation();
			}
		}

		listeners.forEach(Runnable::run);
	}

	private static boolean hasVisibleWindow() {
		for (Window w : Window.getWindows()) {
			if (w.isShowing()) {
				return true;
			}
		}
		return false;
	}

	private static synchronized void updateSystemAppearanceMonitor() {
		if (theme == Theme.System) {
			if (systemAppearancePolling == null) {
				if (systemAppearanceMonitor == null) {
					systemAppearanceMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
						Thread thread = new Thread(r, "SystemAppearanceMonitor");
						thread.setDaemon(true);
						return thread;
					});
				}

				int interval = SystemAppearance.getPollingInterval();
				systemAppearancePolling = systemAppearanceMonitor.scheduleWithFixedDelay(() -> {
					boolean systemDark = SystemAppearance.isDark();
					if (systemDark != dark) {
						SwingUtilities.invokeLater(() -> {
							if (theme == Theme.System) {
								apply(systemDark, true);
							}
						});
					}
				}, interval, interval, TimeUnit.MILLISECONDS);
			}
		} else if (systemAppearancePolling != null) {
			systemAppearancePolling.cancel(false);
			systemAppearancePolling = null;
		}
	}

	private ThemeManager() {
		throw new UnsupportedOperationException();
	}

}
