package dev.davamu.filebotpro.ui.theme;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.plaf.UIResource;

import dev.davamu.filebotpro.ui.ThemeSupport;

/**
 * Re-maps colors that components received explicitly (e.g. {@code setBackground(getPanelBackground())}) when switching between light and dark mode at runtime.
 *
 * Look and feel defaults ({@link UIResource}) are updated by Swing itself, so only explicitly set colors that match a semantic color of the previous theme are migrated.
 */
final class ColorMigration {

	private static final String[] SEMANTIC_KEYS = { "App.surface", "Panel.background", "Table.background", "List.background", "TextField.background", "Label.foreground", "Label.disabledForeground", "App.secondaryText", "Component.borderColor", "Separator.foreground", "Component.accentColor", "Component.linkColor", "App.help.background", "App.help.foreground", "App.success", "App.warning", "App.error", "App.header.background", "App.sidebar.background" };

	private final boolean previousDark;
	private final Map<Integer, String> previousColors = new HashMap<Integer, String>();

	// explicitly set colors before the switch (only colors that are still the same after the look and feel update are stale)
	private final Map<Component, Color[]> previousState = new IdentityHashMap<Component, Color[]>();

	private ColorMigration(boolean previousDark) {
		this.previousDark = previousDark;

		for (String key : SEMANTIC_KEYS) {
			Color c = UIManager.getColor(key);
			if (c != null) {
				previousColors.putIfAbsent(c.getRGB(), key);
			}
		}

		for (Window window : Window.getWindows()) {
			capture(window);
		}
	}

	private void capture(Component c) {
		Color background = c.isBackgroundSet() ? c.getBackground() : null;
		Color foreground = c.isForegroundSet() ? c.getForeground() : null;
		Color grid = c instanceof JTable ? ((JTable) c).getGridColor() : null;

		if (background != null || foreground != null || grid != null) {
			previousState.put(c, new Color[] { background, foreground, grid });
		}

		if (c instanceof Container) {
			for (Component child : ((Container) c).getComponents()) {
				capture(child);
			}
		}
	}

	/**
	 * Capture the semantic colors of the current theme (call before switching).
	 */
	static ColorMigration capture(boolean dark) {
		return new ColorMigration(dark);
	}

	/**
	 * Update all components of all windows (call after switching).
	 */
	void apply() {
		previousState.forEach((c, previous) -> {
			if (previous[0] != null && c.isBackgroundSet() && previous[0].equals(c.getBackground())) {
				Color color = map(previous[0]);
				if (color != null) {
					c.setBackground(color);
				}
			}

			if (previous[1] != null && c.isForegroundSet() && previous[1].equals(c.getForeground())) {
				Color color = map(previous[1]);
				if (color != null) {
					c.setForeground(color);
				}
			}

			if (previous[2] != null && previous[2].equals(((JTable) c).getGridColor())) {
				Color color = map(previous[2]);
				if (color != null) {
					((JTable) c).setGridColor(color);
				}
			}
		});
	}

	private Color map(Color color) {
		if (color == null || color instanceof UIResource) {
			return null;
		}

		String key = previousColors.get(color.getRGB());
		if (key != null) {
			Color current = UIManager.getColor(key);
			return current == null ? null : new Color(current.getRGB(), true);
		}

		Integer original = ThemeSupport.getOriginalColor(color, previousDark);
		if (original != null) {
			return ThemeSupport.getColor(original);
		}

		return null;
	}

}
