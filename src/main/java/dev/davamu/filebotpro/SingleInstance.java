package dev.davamu.filebotpro;

import static java.nio.charset.StandardCharsets.*;
import static java.nio.file.StandardOpenOption.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.Logging.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import dev.davamu.filebotpro.platform.windows.WinAppUtilities;

/**
 * Pass the files of another launch of the desktop app (e.g. Open With in the file manager) to the window that is already
 * open, instead of opening a second window. macOS app bundles are single instance anyway.
 *
 * The Windows Explorer context menu starts one process per selected file, so launches that arrive within a short time of
 * each other are merged into one file list.
 */
public final class SingleInstance {

	private static final String OK = "OK";
	private static final int TIMEOUT = 5000;

	/** how long a later launch waits for the first instance to accept files while that one is still starting up */
	private static final int STARTUP_TIMEOUT = 20000;

	/** launches less than this apart are one selection */
	private static final int SELECTION_WINDOW = 2000;

	/** wait for more launches of the same selection before loading the files */
	private static final int SELECTION_DELAY = 500;

	private static FileLock lock;

	private static final Set<File> selection = new LinkedHashSet<File>();
	private static long lastLaunch;
	private static Consumer<List<File>> handler;
	private static Timer timer;

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
				// first instance (the lock is held until the process exits), later launches wait for the new port file
				Files.deleteIfExists(getPortFile());
				return false;
			}
			channel.close();

			// the running instance may still be starting up (e.g. several files opened at once)
			long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT;
			while (true) {
				try {
					send(files);
					return true;
				} catch (Exception e) {
					if (System.currentTimeMillis() > deadline) {
						throw e;
					}
					Thread.sleep(250);
				}
			}
		} catch (Exception e) {
			debug.log(Level.WARNING, "Failed to pass files to the running instance", e);
			return false;
		}
	}

	private static void send(List<File> files) throws Exception {
		// port, secret token and process id of the running instance
		List<String> instance = Files.readAllLines(getPortFile(), UTF_8);
		int port = Integer.parseInt(instance.get(0).trim());
		String token = instance.get(1).trim();

		// Windows: let the running instance bring its window to the front
		if (Settings.isWindowsApp() && instance.size() > 2) {
			WinAppUtilities.allowSetForegroundWindow(Integer.parseInt(instance.get(2).trim()));
		}

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
			if (!OK.equals(in.readLine())) {
				throw new IOException("No response from the running instance");
			}
		}
	}

	/**
	 * Accept the files of other launches (if this is the first instance). They are kept until a handler is set with
	 * {@link #onLaunch(Consumer)}.
	 *
	 * @param files
	 *            files of this launch, loaded together with the files of other launches of the same selection
	 * @return true if this instance receives the files of other launches
	 */
	public static boolean start(List<File> files) {
		if (lock == null) {
			return false;
		}

		try {
			ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
			String token = UUID.randomUUID().toString();
			Files.write(getPortFile(), List.of(String.valueOf(server.getLocalPort()), token, String.valueOf(ProcessHandle.current().pid())), UTF_8);

			if (files.size() > 0) {
				receive(files);
			}

			Thread thread = new Thread(() -> {
				while (!server.isClosed()) {
					try (Socket socket = server.accept()) {
						socket.setSoTimeout(TIMEOUT);

						BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
						if (!token.equals(in.readLine())) {
							continue; // not from another launch of this application
						}

						receive(in.lines().filter(s -> !s.isBlank()).map(File::new).collect(toList()));

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
			return true;
		} catch (Exception e) {
			debug.log(Level.WARNING, "Failed to start single instance listener", e);
			return false;
		}
	}

	/**
	 * @param launchHandler
	 *            called on the EDT with the files of the current selection (all launches less than a moment apart,
	 *            including this one), or an empty list for a launch without files
	 */
	public static synchronized void onLaunch(Consumer<List<File>> launchHandler) {
		handler = launchHandler;

		if (selection.size() > 0) {
			SwingUtilities.invokeLater(SingleInstance::schedule);
		}
	}

	private static synchronized void receive(List<File> files) {
		if (files.isEmpty()) {
			// bring the window to the front without changing the loaded files
			if (handler != null) {
				Consumer<List<File>> h = handler;
				SwingUtilities.invokeLater(() -> h.accept(List.of()));
			}
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastLaunch > SELECTION_WINDOW) {
			selection.clear();
		}
		lastLaunch = now;
		selection.addAll(files);

		// AWT may not be running yet (onLaunch schedules the files later)
		if (handler != null) {
			SwingUtilities.invokeLater(SingleInstance::schedule);
		}
	}

	private static void schedule() {
		if (timer == null) {
			timer = new Timer(SELECTION_DELAY, evt -> {
				List<File> files;
				Consumer<List<File>> h;
				synchronized (SingleInstance.class) {
					files = List.copyOf(selection);
					h = handler;
				}
				h.accept(files);
			});
			timer.setRepeats(false);
		}
		timer.restart();
	}

	private SingleInstance() {
		throw new UnsupportedOperationException();
	}

}
