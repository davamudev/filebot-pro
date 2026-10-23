package dev.davamu.filebotpro;

import static java.awt.GraphicsEnvironment.*;
import static dev.davamu.filebotpro.ExitCode.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.MediaTypes.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.ui.ThemeSupport.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;
import static dev.davamu.filebotpro.util.FileUtilities.getChildren;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.Frame;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.kohsuke.args4j.CmdLineException;

import dev.davamu.filebotpro.cli.ArgumentBean;
import dev.davamu.filebotpro.cli.ArgumentProcessor;
import dev.davamu.filebotpro.platform.mac.MacAppUtilities;
import dev.davamu.filebotpro.platform.windows.SessionEndHandler;
import dev.davamu.filebotpro.platform.windows.WinAppUtilities;
import dev.davamu.filebotpro.platform.x11.X11AppUtilities;
import dev.davamu.filebotpro.ui.AboutDialog;
import dev.davamu.filebotpro.ui.AppMenuBar;
import dev.davamu.filebotpro.ui.MainFrame;
import dev.davamu.filebotpro.ui.NotificationHandler;
import dev.davamu.filebotpro.ui.PanelBuilder;
import dev.davamu.filebotpro.ui.SinglePanelFrame;
import dev.davamu.filebotpro.ui.UpdateDialog;
import dev.davamu.filebotpro.ui.WelcomeDialog;
import dev.davamu.filebotpro.ui.list.ListPanel;
import dev.davamu.filebotpro.ui.theme.ThemeManager;
import dev.davamu.filebotpro.ui.transfer.FileTransferable;
import dev.davamu.filebotpro.util.PreferencesMap.PreferencesEntry;
import dev.davamu.filebotpro.util.ui.ClickAwayFocusHandler;
import dev.davamu.filebotpro.util.ui.SwingEventBus;

public class Main {

	/** this instance receives the files of later launches (and loads its own file arguments through SingleInstance) */
	private static boolean singleInstance;

	/** redirected output (pipes, files, service logs) is encoded in the legacy code page by default, which loses non-Latin names */
	private static void useUtf8ForRedirectedOutput() {
		java.io.Console console = System.console();
		if (console != null && console.isTerminal()) {
			return;
		}
		if (!System.out.charset().equals(java.nio.charset.StandardCharsets.UTF_8)) {
			System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
		}
		if (!System.err.charset().equals(java.nio.charset.StandardCharsets.UTF_8)) {
			System.setErr(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err), true, java.nio.charset.StandardCharsets.UTF_8));
		}
	}

	public static void main(String[] argv) {
		useUtf8ForRedirectedOutput();

		try {
			// early so that options like -Dapplication.dir apply before anything reads them
			applyEnvironmentOptions(true);

			// parse arguments
			ArgumentBean args = ArgumentBean.parse(argv);

			// just print help message or version string and then exit
			if (args.printHelp()) {
				log.info(String.format("%s%n%n%s", getApplicationIdentifier(), args.usage()));
				System.exit(SUCCESS);
			}

			if (args.printVersion()) {
				log.info(String.join(" / ", getApplicationIdentifier(), getJavaRuntimeIdentifier(), getSystemIdentifier()));
				System.exit(SUCCESS);
			}

			if (args.clearCache() || args.clearUserData() || args.clearHistory()) {
				// clear persistent history
				if (args.clearHistory) {
					log.info("Reset history");
					HistorySpooler.getInstance().clear();
				}

				// clear persistent user preferences
				if (args.clearUserData()) {
					log.info("Reset preferences");
					Settings.forPackage(Main.class).clear();
					getPreferencesBackupFile().delete();
				}

				// clear caches
				if (args.clearCache()) {
					// clear cache must be called manually
					if (System.console() == null || !System.console().isTerminal()) {
						log.severe("`" + getApplicationId() + " -clear-cache` must be called from an interactive console.");
						System.exit(ERROR);
					}

					log.info("Clear cache");
					for (File folder : getChildren(ApplicationFolder.Cache.get(), FOLDERS)) {
						log.fine("* Delete " + folder);
						delete(folder);
					}
				}

				// just clear cache and/or settings and then exit
				System.exit(SUCCESS);
			}

			// export or restore user data and then exit
			if (args.exportData || args.importData) {
				List<String> paths = args.arguments;
				if (paths.size() != 1) {
					throw new CmdLineException(null, "Expecting exactly one zip file: " + paths, null);
				}

				File zip = new File(paths.get(0)).getAbsoluteFile();
				if (args.exportData) {
					log.info(format("Export %s to [%s]", UserData.export(zip), zip));
				} else {
					log.warning("Only restore backups you made yourself: a backup can change system properties and API keys");
					log.info(format("Restore %s from [%s]", UserData.restore(zip), zip));
				}
				System.exit(SUCCESS);
			}

			// make sure we can access application arguments at any time
			setApplicationArguments(args);

			// update system properties
			initializeSystemProperties(args);
			initializeLogging(args);

			// Linux: tiling window managers (i3, sway, ...) need _JAVA_AWT_WM_NONREPARENTING=1 when the JVM starts
			if (!args.runCLI() && !isHeadless()) {
				X11AppUtilities.relaunchForNonReparentingWindowManager();
			}

			// desktop app already open => pass the files to that window and exit, otherwise accept the files of later launches
			if (!args.runCLI() && !isHeadless() && args.mode == null && SingleInstance.isEnabled()) {
				List<File> files = args.getFiles(false);
				if (SingleInstance.forward(files)) {
					System.exit(SUCCESS);
				}
				singleInstance = SingleInstance.start(files);
			}

			// optional native tools and libraries (MediaInfo, fpcalc, ffprobe, ...)
			NativeLibraries.configure();

			// initialize this stuff before anything else
			CacheManager.getInstance();

			// initialize history spooler
			HistorySpooler.getInstance().setPersistentHistoryEnabled(useRenameHistory());
			HistorySpooler.getInstance().setProcessSessionEnabled(args.runCLI());

			// CLI mode => run command-line interface and then exit
			if (args.runCLI()) {
				Runnable updateNotice = "fn:sysinfo".equals(args.script) ? () -> {} : UpdateCheck.startConsoleNotice();
				int status = new ArgumentProcessor().run(args);
				updateNotice.run();
				System.exit(status);
			}

			// just print help page if we can't run any command and also can't start the GUI
			if (isHeadless()) {
				log.info(String.format("%s / %s (headless)%n%n%s", getApplicationIdentifier(), getJavaRuntimeIdentifier(), args.usage()));
				System.exit(ERROR);
			}

			// macOS title bars follow the theme (must be set before AWT starts)
			ThemeManager.configureNativeAppearance();

			// GUI mode => start user interface
			SwingUtilities.invokeLater(() -> {
				startUserInterface(args);

				// run background tasks
				newSwingWorker(() -> onStart(args)).execute();
				newSwingWorker(Main::warmup).execute();
			});
		} catch (CmdLineException e) {
			// illegal arguments => print CLI error message
			log.severe(e::getMessage);
			System.exit(ERROR);
		} catch (Throwable e) {
			// unexpected error => dump stack
			debug.log(Level.SEVERE, "Error during startup", e);
			System.exit(ERROR);
		}
	}

	/**
	 * Initialize the Groovy engine and the format editor in the background, so the first panel that needs them opens instantly
	 */
	private static void warmup() {
		try {
			ListPanel.warmup();
		} catch (Throwable e) {
			debug.log(Level.WARNING, "Failed to warm up format engine", e);
		}
	}

	private static void onStart(ArgumentBean args) throws Exception {
		// publish file arguments (single instance: loaded together with the files of other launches)
		List<File> files = args.getFiles(false);
		if (files.size() > 0 && !singleInstance) {
			SwingEventBus.getInstance().post(new FileTransferable(files));
		}

		// restore preferences from backup if necessary
		try {
			if (Preferences.userNodeForPackage(Main.class).keys().length == 0) {
				File f = getPreferencesBackupFile();
				if (f.exists()) {
					log.fine("Restore user preferences: " + f);
					Settings.restore(f);
				} else {
					log.fine("No user preferences found: " + f);
				}
			}
		} catch (Exception e) {
			debug.log(Level.WARNING, "Failed to restore preferences", e);
		}

		// check if application help should be shown
		if (!"skip".equals(System.getProperty("application.help"))) {
			try {
				checkGettingStarted();
			} catch (Throwable e) {
				debug.log(Level.WARNING, "Failed to show Getting Started help", e);
			}
		}

		// check for application updates
		if (UpdateCheck.isEnabled()) {
			try {
				checkUpdate();
			} catch (Throwable e) {
				debug.log(Level.FINE, "Failed to check for updates", e);
			}
		}
	}

	private static void exitApplication() {
		// let running file operations finish (up to 30 seconds), their history entries are written right after
		dev.davamu.filebotpro.util.ui.ProgressMonitor.awaitRunningTasks(30_000);

		// make sure any long running operations are done now and not later on the
		// shutdown hook thread
		HistorySpooler.getInstance().commit();

		// backup preferences so they can be restored on start if the preferences store has been lost
		try {
			File f = getPreferencesBackupFile();
			log.fine("Store user preferences: " + f);
			Settings.store(f);
		} catch (Exception e) {
			debug.log(Level.WARNING, "Failed to store preferences", e);
		}

		System.exit(0);
	}

	private static void startUserInterface(ArgumentBean args) {
		// Linux / BSD: WM_CLASS must match StartupWMClass of the .desktop file for the dock icon (no-op elsewhere)
		X11AppUtilities.setWindowClass(getApplicationProperty("application.id"));

		// FlatLaf light / dark theme with animated components
		setTheme();

		// clicking on the window background takes the focus away from text fields
		ClickAwayFocusHandler.install();

		// start standard frame or single panel frame
		List<PanelBuilder> panels = args.getPanelBuilders();

		JFrame frame = panels.size() > 1 ? new MainFrame(panels) : new SinglePanelFrame(panels.get(0));
		try {
			restoreWindowBounds(frame, Settings.forPackage(MainFrame.class)); // restore previous size and location
		} catch (Exception e) {
			frame.setLocationRelativeTo(null); // first start: center on screen
		}

		frame.addWindowListener(windowClosed(evt -> {
			evt.getWindow().setVisible(false);

			exitApplication();
		}));

		// Windows: save state and exit orderly when the user logs off or shuts down
		SessionEndHandler.install(Main::exitApplication);

		// configure main window
		if (isMacApp()) {
			// macOS-specific configuration
			MacAppUtilities.initializeApplication(AppMenuBar.createHelp(), files -> {
				SwingEventBus.getInstance().post(new FileTransferable(files));
			}, () -> AboutDialog.showAbout(frame));
		} else if (isWindowsApp()) {
			// Windows-specific configuration
			WinAppUtilities.initializeApplication(getApplicationName());
			frame.setIconImages(ResourceManager.getApplicationIconImages());
		} else {
			// generic Linux / FreeBSD / Solaris configuration
			frame.setIconImages(ResourceManager.getApplicationIconImages());
		}

		// start application
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setVisible(true);

		// receive the files of later launches and bring this window to the front
		SingleInstance.onLaunch(files -> {
			if ((frame.getExtendedState() & JFrame.ICONIFIED) != 0) {
				frame.setExtendedState(frame.getExtendedState() & ~JFrame.ICONIFIED);
			}
			frame.toFront();
			frame.requestFocus();

			if (files.size() > 0) {
				SwingEventBus.getInstance().post(new FileTransferable(files));
			}
		});
	}

	private static File getPreferencesBackupFile() {
		return ApplicationFolder.AppData.resolve("preferences.backup.xml");
	}

	/**
	 * Show update notifications if updates are available
	 */
	private static void checkUpdate() throws Exception {
		UpdateCheck.Update update = UpdateCheck.getAvailableUpdate();
		if (update == null) {
			return;
		}

		// Docker: updating means pulling a new image, a notification is enough
		if ("docker".equals(getApplicationDeployment())) {
			log.info(String.format("%s is available. %s", update.title(), UpdateCheck.getInstructions()));
			return;
		}

		SwingUtilities.invokeLater(() -> new UpdateDialog(JFrame.getFrames()[0], update).setVisible(true));
	}

	/**
	 * Show Getting Started to new users
	 */
	private static void checkGettingStarted() throws Exception {
		PreferencesEntry<String> started = Settings.forPackage(Main.class).entry("getting.started").defaultValue("0");
		if ("0".equals(started.getValue())) {
			started.setValue("1");
			started.flush();

			// show welcome dialog
			SwingUtilities.invokeLater(() -> WelcomeDialog.showWelcome(JFrame.getFrames().length > 0 ? JFrame.getFrames()[0] : null));
		}
	}

	private static void restoreWindowBounds(JFrame window, Settings settings) {
		// store bounds on close
		window.addWindowListener(windowClosed(evt -> {
			// remember maximized state, but keep the normal bounds of the last time the window was not maximized
			boolean maximized = isMaximized(window);
			settings.put("window.maximized", String.valueOf(maximized));

			if (!maximized) {
				settings.put("window.x", String.valueOf(window.getX()));
				settings.put("window.y", String.valueOf(window.getY()));
				settings.put("window.width", String.valueOf(window.getWidth()));
				settings.put("window.height", String.valueOf(window.getHeight()));
			}
		}));

		// restore bounds
		int x = Integer.parseInt(settings.get("window.x"));
		int y = Integer.parseInt(settings.get("window.y"));
		int width = Integer.parseInt(settings.get("window.width"));
		int height = Integer.parseInt(settings.get("window.height"));

		// make sure the window is fully visible (e.g. the monitor has been disconnected or the screen resolution changed)
		window.setBounds(fitToScreen(new Rectangle(x, y, width, height), null));

		// maximize on the screen of the restored bounds
		if (Boolean.parseBoolean(settings.get("window.maximized"))) {
			window.setExtendedState(window.getExtendedState() | Frame.MAXIMIZED_BOTH);
		}
	}

	public static void initializeSystemProperties(ArgumentBean args) {
		System.setProperty("http.agent", String.format("%s/%s", getApplicationName(), getApplicationVersion()));
		System.setProperty("sun.net.client.defaultConnectTimeout", "10000");
		System.setProperty("sun.net.client.defaultReadTimeout", "60000");

		System.setProperty("grape.root", ApplicationFolder.AppData.resolve("grape").getPath());
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");

		// set additional user-defined default system properties
		File userDefinedSystemProperties = ApplicationFolder.AppData.resolve("system.properties");
		if (userDefinedSystemProperties.isFile()) {
			try (FileInputStream in = new FileInputStream(userDefinedSystemProperties)) {
				Properties p = new Properties();
				p.load(in);
				p.forEach((k, v) -> System.setProperty(k.toString(), v.toString()));
			} catch (Exception e) {
				log.log(Level.WARNING, e, e::getMessage);
			}
		}

		// options of a single run take precedence over the defaults in system.properties
		applyEnvironmentOptions(false);

		if (args.unixfs) {
			System.setProperty("unixfs", "true");
		}

		if (args.disableExtendedAttributes) {
			System.setProperty("useExtendedFileAttributes", "false");
			System.setProperty("useCreationDate", "false");
		}

		if (args.disableHistory) {
			System.setProperty("application.rename.history", "false");
		}

		if (args.disableMediaParser) {
			System.setProperty("useMediaParser", "false");
		}

		if (args.disableLocalIndex) {
			System.setProperty("useLocalIndex", "false");
		}
	}

	private static final String ENVIRONMENT_OPTIONS = "FILEBOT_PRO_OPTS";

	// one option per token, single or double quotes keep spaces (e.g. -Dapplication.dir="D:\My Media\FileBot Pro")
	private static final Pattern OPTION_TOKEN = Pattern.compile("(?:[^\\s\"']+|\"[^\"]*\"|'[^']*')+");
	private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"|'([^']*)'");

	/**
	 * Apply the -D system properties in FILEBOT_PRO_OPTS. The installDist scripts already pass the variable to the JVM, but
	 * the native launchers (jpackage) ignore the environment and only read the java-options of their .cfg file.
	 */
	private static void applyEnvironmentOptions(boolean warn) {
		String options = System.getenv(ENVIRONMENT_OPTIONS);
		if (options == null || options.isBlank()) {
			return;
		}

		Matcher token = OPTION_TOKEN.matcher(options);
		while (token.find()) {
			String option = QUOTED.matcher(token.group()).replaceAll("$1$2");
			if (option.startsWith("-D") && option.length() > 2) {
				String[] property = option.substring(2).split("=", 2);
				System.setProperty(property[0], property.length > 1 ? property[1] : "");
			} else if (warn && System.getProperty("jpackage.app-path") != null) {
				// JVM options (e.g. -Xmx4g) cannot be applied once the JVM is running
				log.warning(format("%s: %s only works in the java-options of the .cfg file or in JAVA_TOOL_OPTIONS", ENVIRONMENT_OPTIONS, option));
			}
		}
	}

	public static void initializeLogging(ArgumentBean args) throws IOException {
		// make sure that these folders exist
		ApplicationFolder.TemporaryFiles.get().mkdirs();
		ApplicationFolder.AppData.get().mkdirs();

		if (args.runCLI()) {
			// CLI logging settings
			log.setLevel(args.getLogLevel());
		} else {
			// GUI logging settings
			log.setLevel(Level.INFO);
			log.addHandler(new NotificationHandler(getApplicationName()));

			// log errors to file
			try {
				Handler errorLogHandler = createSimpleFileHandler(ApplicationFolder.AppData.resolve("error.log"), Level.WARNING);
				log.addHandler(errorLogHandler);
				debug.addHandler(errorLogHandler);
			} catch (Exception e) {
				log.log(Level.WARNING, "Failed to initialize error log", e);
			}
		}

		// tee stdout and stderr to log file if --log-file is set
		if (args.logFile != null) {
			Handler logFileHandler = createLogFileHandler(args.getLogFile(), args.logLock, Level.ALL);
			log.addHandler(logFileHandler);
			debug.addHandler(logFileHandler);
		}
	}

}
