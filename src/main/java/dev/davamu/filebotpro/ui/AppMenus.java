package dev.davamu.filebotpro.ui;

import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;
import java.util.logging.Level;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.UIManager;

import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.UserData;
import dev.davamu.filebotpro.UserFiles;
import dev.davamu.filebotpro.util.FileUtilities.ExtensionFileFilter;
import dev.davamu.filebotpro.ui.theme.Theme;
import dev.davamu.filebotpro.ui.theme.ThemeManager;
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;

/**
 * Appearance (light / dark / system) and help menus shown in the navigation rail.
 */
public final class AppMenus {

	public static JButton createAppearanceButton() {
		JButton button = createRailButton(getThemeSymbol(ThemeManager.getTheme()), "Appearance");

		button.addActionListener(evt -> {
			JPopupMenu menu = new JPopupMenu();
			ButtonGroup group = new ButtonGroup();
			for (Theme theme : Theme.values()) {
				JRadioButtonMenuItem item = new JRadioButtonMenuItem(theme.getLabel(), ResourceManager.getSvgIcon(getThemeSymbol(theme), 16, AppMenus::getMenuIconColor));
				item.setSelected(theme == ThemeManager.getTheme());
				if (item.isSelected()) {
					item.putClientProperty(FlatClientProperties.STYLE_CLASS, "semibold");
				}
				item.addActionListener(e -> ThemeManager.setTheme(theme));
				group.add(item);
				menu.add(item);
			}
			showMenu(menu, button);
		});

		// keep the icon in sync with the selected theme
		ThemeManager.addThemeListener(() -> button.setIcon(createRailIcon(getThemeSymbol(ThemeManager.getTheme()))));
		return button;
	}

	public static JButton createHelpButton() {
		JButton button = createRailButton("help", "Help");

		button.addActionListener(evt -> {
			JPopupMenu menu = new JPopupMenu();
			menu.add(createLink("Getting Started", getApplicationProperty("link.intro")));
			menu.add(createLink("Format Expressions", getApplicationProperty("link.format")));
			menu.add(createLink("FAQ", getApplicationProperty("link.faq")));
			menu.add(createLink("Troubleshooting", getApplicationProperty("link.help")));
			menu.addSeparator();

			JMenuItem welcome = new JMenuItem("Show welcome screen");
			welcome.addActionListener(e -> WelcomeDialog.showWelcome(getWindow(button)));
			menu.add(welcome);

			JMenuItem properties = new JMenuItem("System properties…");
			properties.addActionListener(e -> SystemPropertiesDialog.show(getWindow(button)));
			menu.add(properties);

			JMenuItem export = new JMenuItem("Export user data…");
			export.addActionListener(e -> exportUserData(e));
			menu.add(export);

			JMenuItem restore = new JMenuItem("Restore user data…");
			restore.addActionListener(e -> restoreUserData(e));
			menu.add(restore);

			JMenuItem about = new JMenuItem("About " + getApplicationName());
			about.addActionListener(e -> AboutDialog.showAbout(getWindow(button)));
			menu.add(about);

			showMenu(menu, button);
		});

		return button;
	}

	private static void exportUserData(ActionEvent evt) {
		File zip = UserFiles.showSaveDialogSelectFile(false, new File(UserData.getDefaultFileName()), "Export user data", evt);
		if (zip == null) {
			return;
		}

		try {
			UserData.export(zip);
			log.info(String.format("Settings, presets and history exported to %s", zip.getName()));
		} catch (Exception e) {
			log.log(Level.WARNING, "Failed to export user data: " + e.getMessage(), e);
		}
	}

	private static void restoreUserData(ActionEvent evt) {
		List<File> files = UserFiles.showLoadDialogSelectFiles(false, false, null, new ExtensionFileFilter("zip"), "Restore user data", evt);
		if (files.isEmpty()) {
			return;
		}

		Component parent = getWindow(evt.getSource());
		String message = "Settings and presets will be replaced and the history will be merged.\nRestart the application afterwards to apply all settings.";
		if (JOptionPane.showConfirmDialog(parent, message, "Restore user data", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
			return;
		}

		try {
			UserData.restore(files.get(0));
			log.info("User data restored. Please restart the application.");
		} catch (Exception e) {
			log.log(Level.WARNING, "Failed to restore user data: " + e.getMessage(), e);
		}
	}

	private static JMenuItem createLink(String title, String uri) {
		JMenuItem item = new JMenuItem(title);
		item.addActionListener(evt -> openURI(uri));
		return item;
	}

	private static void showMenu(JPopupMenu menu, Component invoker) {
		menu.show(invoker, invoker.getWidth() + 6, invoker.getHeight() - menu.getPreferredSize().height);
	}

	private static JButton createRailButton(String symbol, String tooltip) {
		JButton button = new JButton(createRailIcon(symbol));
		button.setToolTipText(tooltip);
		button.getAccessibleContext().setAccessibleName(tooltip);
		button.putClientProperty(FlatClientProperties.STYLE_CLASS, "ghost");
		button.putClientProperty(FlatClientProperties.STYLE, "arc: 12; margin: 8,8,8,8");
		button.setFocusable(false);
		return button;
	}

	private static FlatSVGIcon createRailIcon(String symbol) {
		return ResourceManager.getSvgIcon(symbol, 20, () -> UIManager.getColor("App.sidebar.foreground"));
	}

	private static Color getMenuIconColor() {
		return UIManager.getColor("MenuItem.foreground");
	}

	private static String getThemeSymbol(Theme theme) {
		switch (theme) {
		case Light:
			return "theme-light";
		case Dark:
			return "theme-dark";
		default:
			return "theme-system";
		}
	}

	private AppMenus() {
		throw new UnsupportedOperationException();
	}

}
