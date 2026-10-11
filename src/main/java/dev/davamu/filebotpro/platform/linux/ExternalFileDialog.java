package dev.davamu.filebotpro.platform.linux;

import static java.nio.charset.StandardCharsets.*;
import static java.util.stream.Collectors.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Native file dialogs of the Linux desktop via zenity (GNOME and most others) or kdialog (KDE), so users get the file
 * dialog of their desktop with bookmarks, recent files and network places instead of the Swing file chooser.
 */
public enum ExternalFileDialog {

	ZENITY("zenity"), KDIALOG("kdialog");

	private final String command;

	private ExternalFileDialog(String command) {
		this.command = command;
	}

	/**
	 * @return kdialog on KDE, zenity elsewhere, or null if neither is installed
	 */
	public static ExternalFileDialog getAvailable() {
		String desktop = String.valueOf(System.getenv("XDG_CURRENT_DESKTOP")).toUpperCase(Locale.ROOT);
		List<ExternalFileDialog> order = desktop.contains("KDE") ? List.of(KDIALOG, ZENITY) : List.of(ZENITY, KDIALOG);
		return order.stream().filter(ExternalFileDialog::isInstalled).findFirst().orElse(null);
	}

	private boolean isInstalled() {
		String path = System.getenv("PATH");
		return path != null && Arrays.stream(path.split(File.pathSeparator)).anyMatch(folder -> new File(folder, command).canExecute());
	}

	/**
	 * @return selected files, an empty list if the dialog was cancelled
	 */
	public List<File> showOpenDialog(String title, boolean folderMode, boolean multiSelection, File defaultFile, String filterName, String[] extensions) throws IOException, InterruptedException {
		List<String> args = new ArrayList<String>();
		String start = getStartPath(defaultFile, folderMode);

		if (this == ZENITY) {
			args.addAll(List.of(command, "--file-selection", "--title=" + title, "--separator=\n"));
			if (folderMode) {
				args.add("--directory");
			}
			if (multiSelection) {
				args.add("--multiple");
			}
			if (start != null) {
				args.add("--filename=" + start);
			}
			if (extensions != null && !folderMode) {
				args.add("--file-filter=" + filterName.replace('|', ' ') + " | " + getPatterns(extensions));
				args.add("--file-filter=* | *");
			}
		} else {
			args.addAll(List.of(command, "--title", title));
			if (folderMode) {
				args.addAll(List.of("--getexistingdirectory", start == null ? "." : start));
			} else {
				args.addAll(List.of("--getopenfilename", start == null ? "." : start));
				if (extensions != null) {
					args.add(getPatterns(extensions) + "|" + filterName);
				}
				if (multiSelection) {
					args.addAll(List.of("--multiple", "--separate-output"));
				}
			}
		}

		return run(args).stream().map(File::new).collect(toList());
	}

	/**
	 * @return selected file, or null if the dialog was cancelled
	 */
	public File showSaveDialog(String title, boolean folderMode, File defaultFile) throws IOException, InterruptedException {
		List<String> args = new ArrayList<String>();
		String start = defaultFile == null ? null : defaultFile.getAbsolutePath();

		if (this == ZENITY) {
			args.addAll(List.of(command, "--file-selection", "--title=" + title, "--save", "--confirm-overwrite"));
			if (folderMode) {
				args.add("--directory");
			}
			if (start != null) {
				args.add("--filename=" + start);
			}
		} else {
			args.addAll(List.of(command, "--title", title, folderMode ? "--getexistingdirectory" : "--getsavefilename", start == null ? "." : start));
		}

		List<String> selection = run(args);
		return selection.isEmpty() ? null : new File(selection.get(0));
	}

	private static String getStartPath(File defaultFile, boolean folderMode) {
		if (defaultFile == null) {
			return null;
		}
		// zenity opens the folder of a path that ends with a slash
		if (defaultFile.isDirectory()) {
			return defaultFile.getAbsolutePath() + "/";
		}
		File folder = defaultFile.getAbsoluteFile().getParentFile();
		return folder == null || !folder.isDirectory() ? null : folderMode ? folder.getPath() + "/" : defaultFile.getAbsolutePath();
	}

	private static String getPatterns(String[] extensions) {
		return Arrays.stream(extensions).map(e -> "*." + e).collect(joining(" "));
	}

	private static List<String> run(List<String> args) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start();
		String output = new String(process.getInputStream().readAllBytes(), UTF_8);

		// exit code 1 means cancel
		if (process.waitFor() != 0) {
			return List.of();
		}
		return output.lines().map(String::strip).filter(s -> !s.isEmpty()).collect(toList());
	}

}
