package dev.davamu.filebotpro.ui;

import java.awt.Color;
import java.awt.Insets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.UIManager;
import javax.swing.border.Border;

import dev.davamu.filebotpro.ui.theme.Theme;
import dev.davamu.filebotpro.ui.theme.ThemeManager;
import dev.davamu.filebotpro.util.ui.MatteColorBorder;
import dev.davamu.filebotpro.util.ui.ProgressIndicator;
import dev.davamu.filebotpro.util.ui.RoundBorder;
import dev.davamu.filebotpro.util.ui.notification.SeparatorBorder.Position;

/**
 * Semantic colors and borders for custom painted components.
 *
 * Colors are looked up from the active look and feel at call time, so components should query them when painting (or in updateUI) to follow runtime theme switches.
 */
public final class ThemeSupport {

	public static void setTheme() {
		ThemeManager.install();
	}

	public static Theme getTheme() {
		return ThemeManager.getTheme();
	}

	public static boolean isDark() {
		return ThemeManager.isDark();
	}

	public static Color getAccentColor() {
		return ui("Component.accentColor", ThemeManager.ACCENT);
	}

	// adapted color -> original color (needed to migrate hard-coded colors when the theme is switched at runtime)
	private static final Map<Integer, Integer> lightColors = new ConcurrentHashMap<Integer, Integer>();
	private static final Map<Integer, Integer> darkColors = new ConcurrentHashMap<Integer, Integer>();

	/**
	 * Adapt a hard-coded light theme color to the current theme (dark mode inverts the lightness and mutes the saturation).
	 */
	public static Color getColor(int rgb) {
		Color color = new Color(rgb);
		if (isDark()) {
			Color dark = toDark(color);
			darkColors.putIfAbsent(dark.getRGB(), rgb);
			return dark;
		}
		lightColors.putIfAbsent(color.getRGB(), rgb);
		return color;
	}

	/**
	 * @return the original light theme color for a color previously returned by {@link #getColor(int)} for the given appearance
	 */
	public static Integer getOriginalColor(Color adapted, boolean dark) {
		return (dark ? darkColors : lightColors).get(adapted.getRGB());
	}

	/**
	 * Background of content areas (lists, tables, headers), which is white in light mode.
	 */
	public static Color getPanelBackground() {
		return ui("App.surface", Color.white);
	}

	public static Color getLabelForeground() {
		return ui("Label.foreground", Color.black);
	}

	public static Color getSecondaryForeground() {
		return ui("App.secondaryText", Color.gray);
	}

	public static Color getHelpPanelBackground() {
		return ui("App.help.background", new Color(0xFFF7ED));
	}

	public static Border getHelpPanelBorder() {
		return new RoundBorder("App.help.border", 12, new Insets(1, 1, 1, 1));
	}

	public static Color getErrorColor() {
		return ui("App.error", Color.red);
	}

	public static Color getLinkColor() {
		return ui("Component.linkColor", getAccentColor());
	}

	public static Color getActiveColor() {
		return getAccentColor();
	}

	public static Color getPassiveColor() {
		return ui("Label.disabledForeground", Color.lightGray);
	}

	public static Color getVerificationColor() {
		return ui("App.success", new Color(0x16A34A));
	}

	public static Color getPanelSelectionBorderColor() {
		return getAccentColor();
	}

	public static Color getBlankBackgroundColor() {
		return ui("List.background", Color.white);
	}

	public static Border getRoundBorder() {
		return new RoundBorder("App.surfaceBorder", 12, new Insets(1, 1, 1, 1));
	}

	public static Border getSeparatorBorder(Position position) {
		switch (position) {
		case TOP:
			return new MatteColorBorder(1, 0, 0, 0, "Separator.foreground");
		case LEFT:
			return new MatteColorBorder(0, 1, 0, 0, "Separator.foreground");
		case RIGHT:
			return new MatteColorBorder(0, 0, 0, 1, "Separator.foreground");
		default:
			return new MatteColorBorder(0, 0, 1, 0, "Separator.foreground");
		}
	}

	public static Border getHorizontalRule() {
		return new MatteColorBorder(0, 0, 1, 0, "Separator.foreground");
	}

	public static ProgressIndicator getProgressIndicator() {
		return new ProgressIndicator(getAccentColor(), withAlpha(getLabelForeground(), 0.18f));
	}

	public static Color withAlpha(Color color, float alpha) {
		return new Color(((int) ((alpha * 255)) << 24) | (color.getRGB() & 0x00FFFFFF), true);
	}

	public static Color blend(Color c1, Color c2, float ratio) {
		float r = Math.max(0, Math.min(1, ratio));
		return new Color(Math.round(c1.getRed() + (c2.getRed() - c1.getRed()) * r), Math.round(c1.getGreen() + (c2.getGreen() - c1.getGreen()) * r), Math.round(c1.getBlue() + (c2.getBlue() - c1.getBlue()) * r), Math.round(c1.getAlpha() + (c2.getAlpha() - c1.getAlpha()) * r));
	}

	private static Color ui(String key, Color defaultColor) {
		Color c = UIManager.getColor(key);
		return c != null ? c : defaultColor;
	}

	private static Color toDark(Color c) {
		float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);

		// invert brightness while keeping very dark text readable and very light backgrounds subtle
		float brightness = 1 - hsb[2];
		brightness = 0.1f + brightness * 0.8f;

		float saturation = hsb[1] * 0.85f;
		return withAlpha(Color.getHSBColor(hsb[0], saturation, brightness), c.getAlpha() / 255f);
	}

	private ThemeSupport() {
		throw new UnsupportedOperationException();
	}

}
