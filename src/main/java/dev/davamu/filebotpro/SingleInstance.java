package dev.davamu.filebotpro;

import static java.nio.charset.StandardCharsets.*;
import static java.nio.file.StandardOpenOption.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.Logging.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

import javax.swing.SwingUtilities;

/**
 * Pass the files of another launch of the desktop app (e.g. Open With in the file manager) to the window that is already
 * open, instead of opening a second window. macOS app bundles are single instance anyway.
 */
public final class SingleInstance {

	private static final String OK = "OK";
	private static final int TIMEOUT = 5000;

	private static FileLock lock;

	public static boolean isEnabled() {
		return !"false".equals(System.getProperty("application.single.instance")) && !Settings.isMacApp();
	}

	private static Path getLockFile() {
		return ApplicationFolder.AppData.resolve("instance.lock").toPath();
	}

	private static Path getPortFile() {
		return ApplicationFolder.AppData.resolve("instance.port").toPath();
	}

	/**
	 * @return true if the files have been passed to the running instance, false if this is the first instance or if the
	 *         running instance doesn't respond
	 */
	public static boolean forward(List<File> files) {
		try {
			FileChannel channel = FileChannel.open(getLockFile(), CREATE, WRITE);
			lock = channel.tryLock();
			if (lock != null) {
				return false; // first instance (the lock is held until the process exits)
			}
			channel.close();

			// port and secret token of the running instance
			List<String> instance = Files.readAllLines(getPortFile(), UTF_8);
			int port = Integer.parseInt(instance.get(0).trim());
			String token = instance.get(1).trim();

			try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
				socket.setSoTimeout(TIMEOUT);

				Writer out = new OutputStreamWriter(socket.getOutputStream(), UTF_8);
				out.write(token + "\n");
				for (File f : files) {
					out.write(f.getAbsolutePath() + "\n");
				}
				out.flush();
				socket.shutdownOutput();

				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
				return OK.equals(in.readLine());
			}
		} catch (Exception e) {
			debug.log(Level.WARNING, "Failed to pass files to the running instance", e);
			return false;
		}
	}

	/**
	 * Receive the files of other launches (if this is the first instance)
	 *
	 * @param handler
	 *            called on the EDT with the files of another launch (an empty list if it has no files)
	 */
	public static void listen(Consumer<List<File>> handler) {
		if (lock == null) {
			return;
		}

		try {
			ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
			String token = UUID.randomUUID().toString();
			Files.write(getPortFile(), List.of(String.valueOf(server.getLocalPort()), token), UTF_8);

			Thread thread = new Thread(() -> {
				while (!server.isClosed()) {
					try (Socket socket = server.accept()) {
						socket.setSoTimeout(TIMEOUT);

						BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
						if (!token.equals(in.readLine())) {
							continue; // not from another launch of this application
						}

						List<File> files = in.lines().filter(s -> !s.isBlank()).map(File::new).collect(toList());
						SwingUtilities.invokeLater(() -> handler.accept(files));

						Writer out = new OutputStreamWriter(socket.getOutputStream(), UTF_8);
						out.write(OK + "\n");
						out.flush();
					} catch (Exception e) {
						debug.log(Level.WARNING, "Failed to receive files from another instance", e);
					}
				}
			}, "SingleInstance");

			thread.setDaemon(true);
			thread.start();
		} catch (Exception e) {
			debug.log(Level.WARNING, "Failed to start single instance listener", e);
		}
	}

	private SingleInstance() {
		throw new UnsupportedOperationException();
	}

}
