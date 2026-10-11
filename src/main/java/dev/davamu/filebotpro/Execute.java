package dev.davamu.filebotpro;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.Logging.*;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;

import com.sun.jna.Platform;

import dev.davamu.filebotpro.util.ByteBufferOutputStream;
import dev.davamu.filebotpro.util.FileUtilities;

public class Execute {

	private static final int BUFFER_SIZE = 8 * 1024;

	public static CharSequence execute(String... command) throws IOException {
		return execute(asList(command), null);
	}

	public static CharSequence execute(String[] command, File directory) throws IOException {
		return execute(asList(command), directory);
	}

	public static CharSequence execute(String[]... command) throws IOException {
		return execute(stream(command).flatMap(a -> stream(a)).collect(toList()), null);
	}

	public static CharSequence execute(List<String> command, File directory) throws IOException {
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.redirectError(Redirect.INHERIT);

		if (directory != null) {
			processBuilder.directory(directory);
		}

		Process process = processBuilder.start();

		try (ByteBufferOutputStream bb = new ByteBufferOutputStream(BUFFER_SIZE)) {
			bb.transferFully(process.getInputStream());

			int exitCode = process.waitFor();
			CharSequence output = UTF_8.decode(bb.getByteBuffer());

			// DEBUG
			debug.finest(format("Execute %s%n%s", command, output));

			if (exitCode != 0) {
				throw new ExecuteException(command, exitCode);
			}

			return output;
		} catch (InterruptedException e) {
			throw new IOException(String.format("%s timed out", command), e);
		}
	}

	/**
	 * Find the given command in the PATH, same as the shell would (e.g. ffprobe => C:\ffmpeg\bin\ffprobe.exe or action => C:\Tools\action.cmd)
	 *
	 * @return executable file or null if the command cannot be found
	 */
	public static File which(String command) {
		List<String> extensions = getExecutableExtensions(command);

		// absolute or relative path (e.g. C:\Tools\action or ./action.sh)
		if (command.contains("/") || command.contains(File.separator)) {
			return findExecutable(new File(command).getAbsoluteFile(), extensions);
		}

		String path = System.getenv("PATH");
		if (path == null) {
			return null;
		}

		for (String folder : path.split(File.pathSeparator)) {
			// PATH entries may be quoted on Windows
			folder = folder.trim().replace("\"", "");
			if (folder.length() > 0) {
				File executable = findExecutable(new File(folder, command), extensions);
				if (executable != null) {
					return executable;
				}
			}
		}
		return null;
	}

	private static List<String> getExecutableExtensions(String command) {
		if (!Platform.isWindows()) {
			return List.of("");
		}

		// command with file extension (e.g. action.bat) or try each PATHEXT extension (e.g. .COM;.EXE;.BAT;.CMD)
		String pathext = System.getenv("PATHEXT");
		List<String> extensions = new ArrayList<String>();
		if (FileUtilities.hasExtension(command, "exe", "com", "bat", "cmd", "ps1")) {
			extensions.add("");
		}
		stream((pathext == null ? ".COM;.EXE;.BAT;.CMD" : pathext).split(";")).map(String::trim).filter(s -> s.startsWith(".")).forEach(extensions::add);
		return extensions;
	}

	private static File findExecutable(File file, List<String> extensions) {
		for (String extension : extensions) {
			File executable = extension.isEmpty() ? file : new File(file.getPath() + extension.toLowerCase());
			if (executable.isFile() && executable.canExecute()) {
				return executable;
			}
		}
		return null;
	}

	public static void system(String... command) throws IOException {
		system(asList(command), null);
	}

	public static void system(String[] command, File directory) throws IOException {
		system(asList(command), directory);
	}

	public static void system(List<String> command, File directory) throws IOException {
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.inheritIO();

		if (directory != null) {
			processBuilder.directory(directory);
		}

		// DEBUG
		debug.finest(format("Execute %s", command));

		try {
			int exitCode = processBuilder.start().waitFor();
			if (exitCode != 0) {
				throw new ExecuteException(command, exitCode);
			}
		} catch (InterruptedException e) {
			throw new IOException(String.format("%s timed out", command), e);
		}
	}

}
