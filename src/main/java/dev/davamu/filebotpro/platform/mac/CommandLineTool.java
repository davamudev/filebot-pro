package dev.davamu.filebotpro.platform.mac;

import static dev.davamu.filebotpro.Settings.*;
import static java.nio.charset.StandardCharsets.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.sun.jna.Platform;

/**
 * The filebot-pro command for the Terminal: a link in /usr/local/bin to the launcher of the app bundle, the same link
 * the .pkg installer creates. Installing and removing it asks for the administrator password.
 */
public final class CommandLineTool {

	private static final Path FOLDER = Path.of("/usr/local/bin");

	public static Path getLink() {
		return FOLDER.resolve(getApplicationId());
	}

	/**
	 * @return launcher of the app bundle, or null if the app was not started from a jpackage app bundle on macOS
	 */
	public static Path getLauncher() {
		String launcher = System.getProperty("jpackage.app-path");
		return Platform.isMac() && launcher != null && !launcher.isEmpty() ? Path.of(launcher) : null;
	}

	public static boolean isAvailable() {
		return getLauncher() != null;
	}

	/**
	 * @return true if the link exists and leads to the launcher of this app bundle (false for a link to a moved or
	 *         other copy of the app)
	 */
	public static boolean isInstalled() {
		try {
			return isAvailable() && Files.isSameFile(getLink(), getLauncher());
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * @return false if the user canceled the password prompt
	 */
	public static boolean install() throws IOException {
		String script = "do shell script \"mkdir -p \" & quoted form of (item 1 of argv) & \" && ln -sf \" & quoted form of (item 2 of argv) & \" \" & quoted form of (item 3 of argv)";
		String prompt = String.format("%s wants to add the %s command to %s.", getApplicationName(), getApplicationId(), FOLDER);
		return runAsAdministrator(script, prompt, FOLDER.toString(), getLauncher().toString(), getLink().toString());
	}

	/**
	 * @return false if the user canceled the password prompt
	 */
	public static boolean remove() throws IOException {
		String script = "do shell script \"rm -f \" & quoted form of (item 1 of argv)";
		String prompt = String.format("%s wants to remove the %s command from %s.", getApplicationName(), getApplicationId(), FOLDER);
		return runAsAdministrator(script, prompt, getLink().toString());
	}

	/**
	 * Run a do shell script command with administrator privileges, the arguments are passed as argv (no quoting
	 * required) and the prompt is shown in the system password dialog
	 */
	private static boolean runAsAdministrator(String script, String prompt, String... args) throws IOException {
		List<String> command = new ArrayList<String>();
		command.add("osascript");
		command.add("-e");
		command.add("on run argv");
		command.add("-e");
		command.add(script + " with prompt (item " + (args.length + 1) + " of argv) with administrator privileges");
		command.add("-e");
		command.add("end run");
		command.addAll(List.of(args));
		command.add(prompt);

		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		try {
			String output = new String(process.getInputStream().readAllBytes(), UTF_8).trim();
			if (process.waitFor() == 0) {
				return true;
			}
			// error -128: user canceled
			if (output.contains("-128")) {
				return false;
			}
			throw new IOException(output);
		} catch (InterruptedException e) {
			throw new IOException("osascript interrupted", e);
		}
	}

	private CommandLineTool() {
		throw new UnsupportedOperationException();
	}

}
