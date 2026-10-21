package dev.davamu.filebotpro;

import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.Settings.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.output.CloseShieldOutputStream;

/**
 * Export all user data (settings, presets, rename history, API keys) into a single zip file and restore it later or on another computer
 */
public final class UserData {

	private static final String PREFERENCES = "preferences.xml";
	private static final String HISTORY = "history.xml";

	// plain files in the user folder that are copied as they are
	private static final String[] FILES = { "api-keys.properties", "system.properties", "mediainfo-views.json", "mediainfo-column-widths.json", "pad.groovy" };

	public static List<String> export(File zip) throws Exception {
		List<String> entries = new ArrayList<String>();

		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip.toPath()))) {
			// settings and presets
			out.putNextEntry(new ZipEntry(PREFERENCES));
			Preferences.userNodeForPackage(UserData.class).exportSubtree(CloseShieldOutputStream.wrap(out));
			out.closeEntry();
			entries.add(PREFERENCES);

			// rename history (including the history of this session)
			out.putNextEntry(new ZipEntry(HISTORY));
			History.exportHistory(HistorySpooler.getInstance().getCompleteHistory(), CloseShieldOutputStream.wrap(out));
			out.closeEntry();
			entries.add(HISTORY);

			for (String name : FILES) {
				File f = ApplicationFolder.AppData.resolve(name);
				if (f.isFile()) {
					out.putNextEntry(new ZipEntry(name));
					Files.copy(f.toPath(), out);
					out.closeEntry();
					entries.add(name);
				}
			}
		}

		return entries;
	}

	public static List<String> restore(File zip) throws Exception {
		List<String> entries = new ArrayList<String>();

		try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip.toPath()))) {
			for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
				String name = entry.getName();
				byte[] data = in.readAllBytes();

				if (name.equals(PREFERENCES)) {
					// replace current settings and presets
					Settings.forPackage(UserData.class).clear();
					Preferences.importPreferences(new ByteArrayInputStream(data));
					entries.add(name);
				} else if (name.equals(HISTORY)) {
					// merge with the current history (entries that already exist are skipped)
					History history = History.importHistory(new ByteArrayInputStream(data));
					History current = HistorySpooler.getInstance().getCompleteHistory();
					History missing = new History();
					history.sequences().stream().filter(s -> !current.sequences().contains(s)).forEach(missing::add);
					HistorySpooler.getInstance().append(missing);
					HistorySpooler.getInstance().commit();
					entries.add(name);
				} else if (isUserFile(name)) {
					File f = ApplicationFolder.AppData.resolve(name);
					if (f.exists()) {
						// keep the current file just in case
						Files.copy(f.toPath(), ApplicationFolder.AppData.resolve(name + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
					}
					Files.write(f.toPath(), data);
					entries.add(name);
				} else {
					debug.warning("Ignore unknown entry: " + name);
				}
			}
		}

		if (!entries.contains(PREFERENCES)) {
			throw new IOException("Not a user data backup: " + zip);
		}

		return entries;
	}

	private static boolean isUserFile(String name) {
		for (String f : FILES) {
			if (f.equals(name)) {
				return true;
			}
		}
		return false;
	}

	public static String getDefaultFileName() {
		return getApplicationName().replace(' ', '-') + "-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".zip";
	}

	private UserData() {
		throw new UnsupportedOperationException();
	}

}
