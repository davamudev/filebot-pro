package dev.davamu.filebotpro.ui;

import static java.nio.charset.StandardCharsets.*;
import static dev.davamu.filebotpro.Logging.*;

import java.awt.Component;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.Properties;
import java.util.logging.Level;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.formdev.flatlaf.FlatClientProperties;

import dev.davamu.filebotpro.ApplicationFolder;
import net.miginfocom.swing.MigLayout;

/**
 * Editor for the system.properties file in the user folder, which is applied on startup (e.g. proxy settings, API keys or the media
 * parser) so that the settings don't have to be passed as -D options to every launcher
 */
public final class SystemPropertiesDialog {

	public static final String FILE_NAME = "system.properties";

	private static final String TEMPLATE = String.join("\n", //
			"# System properties applied on startup (restart the application to apply changes)", //
			"# e.g.", //
			"# http.proxyHost=proxy.example.com", //
			"# http.proxyPort=8080", //
			"# https.proxyHost=proxy.example.com", //
			"# https.proxyPort=8080", //
			"# apikey.themoviedb=YOUR_API_KEY", //
			"# dev.davamu.filebotpro.media.parser=ffprobe", //
			"# flatlaf.animation=false", //
			"");

	public static void show(Component parent) {
		File file = ApplicationFolder.AppData.resolve(FILE_NAME);

		JTextArea editor = new JTextArea(18, 64);
		editor.putClientProperty(FlatClientProperties.STYLE_CLASS, "monospaced");
		editor.setTabSize(4);

		try {
			editor.setText(file.isFile() ? Files.readString(file.toPath(), UTF_8) : TEMPLATE);
			editor.setCaretPosition(0);
		} catch (Exception e) {
			log.log(Level.WARNING, "Failed to read " + file + ": " + e.getMessage(), e);
			return;
		}

		JPanel panel = new JPanel(new MigLayout("insets 0, fill, wrap 1", "[grow, fill]", "[][grow, fill]"));
		panel.add(new JLabel("<html>One <b>name=value</b> per line, saved to " + file.getPath() + ".<br>Restart the application to apply the changes.</html>"));
		panel.add(new JScrollPane(editor));

		while (JOptionPane.showConfirmDialog(parent, panel, "System Properties", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
			try {
				// make sure the file can be read on startup
				new Properties().load(new StringReader(editor.getText()));

				Files.writeString(file.toPath(), editor.getText(), UTF_8);
				log.info("System properties saved. Please restart the application.");
				return;
			} catch (Exception e) {
				JOptionPane.showMessageDialog(parent, "Invalid system properties: " + e.getMessage(), "System Properties", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private SystemPropertiesDialog() {
		throw new UnsupportedOperationException();
	}

}
