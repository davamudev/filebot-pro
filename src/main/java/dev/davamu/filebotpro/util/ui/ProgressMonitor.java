package dev.davamu.filebotpro.util.ui;

import static dev.davamu.filebotpro.Logging.*;

import java.awt.Dialog.ModalityType;
import java.awt.Frame;
import java.awt.Taskbar;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.formdev.flatlaf.FlatClientProperties;

import net.miginfocom.swing.MigLayout;

/**
 * Runs a long-running task on a background thread and shows a modal progress dialog if the task takes more than a moment.
 *
 * Calling {@link FutureTask#get()} on the Event Dispatch Thread keeps the user interface responsive (the dialog runs a secondary event loop) until the task is done.
 */
public class ProgressMonitor {

	private static final int SHOW_DELAY = 400; // milliseconds

	private static final java.util.List<Thread> RUNNING = new java.util.concurrent.CopyOnWriteArrayList<Thread>();

	/**
	 * Wait for running tasks (e.g. a batch of file operations) before the application exits, so the operation and its history entry are not cut off.
	 */
	public static void awaitRunningTasks(long timeoutMillis) {
		long deadline = System.currentTimeMillis() + timeoutMillis;
		for (Thread thread : RUNNING) {
			long remaining = deadline - System.currentTimeMillis();
			if (remaining <= 0) {
				return;
			}
			try {
				thread.join(remaining);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	public static <T> FutureTask<T> runTask(String title, String header, ProgressWorker<T> worker) {
		MonitoredTask<T> task = new MonitoredTask<T>(title, header, worker);

		Thread thread = new Thread(() -> {
			try {
				task.run();
			} finally {
				RUNNING.remove(Thread.currentThread());
			}
		}, "ProgressMonitor-" + title);
		thread.setDaemon(true);
		RUNNING.add(thread);
		thread.start();

		// show progress dialog only if the operation takes a while
		SwingUtilities.invokeLater(() -> SwingUI.invokeLater(SHOW_DELAY, task::showDialog));

		return task;
	}

	@FunctionalInterface
	public interface ProgressWorker<T> {
		T call(Consumer<String> message, BiConsumer<Long, Long> progress, Supplier<Boolean> cancelled) throws Exception;
	}

	private static class MonitoredTask<T> extends FutureTask<T> {

		private final String title;
		private final String header;

		private final ProgressState state;

		private JDialog dialog;
		private boolean dialogShown = false;

		public MonitoredTask(String title, String header, ProgressWorker<T> worker) {
			this(title, header, worker, new ProgressState());
		}

		private MonitoredTask(String title, String header, ProgressWorker<T> worker, ProgressState state) {
			super(callable(worker, state));
			this.title = title;
			this.header = header;
			this.state = state;
			this.state.task = this;
		}

		private static <T> Callable<T> callable(ProgressWorker<T> worker, ProgressState state) {
			return () -> worker.call(state::setMessage, state::setProgress, state::isCancelled);
		}

		@Override
		protected void done() {
			SwingUtilities.invokeLater(() -> {
				if (dialog != null) {
					dialog.dispose();
				}
			});
		}

		@Override
		public T get() throws InterruptedException, ExecutionException {
			// keep the UI alive while waiting on the EDT, but don't flash the dialog for quick operations
			if (SwingUtilities.isEventDispatchThread() && !isDone()) {
				try {
					return super.get(SHOW_DELAY, TimeUnit.MILLISECONDS);
				} catch (TimeoutException e) {
					showDialog();
				}
			}
			return super.get();
		}

		public void showDialog() {
			if (isDone() || dialogShown) {
				return;
			}
			dialogShown = true;

			try {
				dialog = createDialog();
				dialog.setVisible(true); // blocks until the dialog is disposed
			} catch (Exception e) {
				debug.log(Level.WARNING, e, e::toString);
			}
		}

		private JDialog createDialog() {
			Window owner = SwingUI.getWindow(javax.swing.FocusManager.getCurrentManager().getActiveWindow());
			JDialog d = new JDialog(owner, title, ModalityType.APPLICATION_MODAL);
			d.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

			JLabel headerLabel = new JLabel(header);
			headerLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "h4");

			JLabel messageLabel = new JLabel(" ");
			messageLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");

			JProgressBar progressBar = new JProgressBar();
			progressBar.setIndeterminate(true);
			progressBar.putClientProperty(FlatClientProperties.PROGRESS_BAR_LARGE_HEIGHT, true);

			JLabel remainingLabel = new JLabel(" ");
			remainingLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");

			JButton cancel = new JButton("Cancel");
			cancel.addActionListener(evt -> cancel(true));

			// fixed width, long file names are truncated instead of pushing the progress bar and the cancel button out of the dialog
			JPanel content = new JPanel(new MigLayout("insets 20 24 18 24, fillx, wrap 1", "[420lp!, fill]", "[]4[]14[]18[]"));
			content.add(headerLabel, "wmin 0");
			content.add(messageLabel, "wmin 0");
			content.add(progressBar);
			content.add(remainingLabel, "split 2, growx");
			content.add(cancel, "align right, growx 0");
			d.setContentPane(content);

			TaskbarProgress taskbar = new TaskbarProgress(owner);
			long start = System.currentTimeMillis();

			// ESC and window close button cancel the task
			content.registerKeyboardAction(evt -> cancel(true), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
			d.addWindowListener(new WindowAdapter() {

				@Override
				public void windowClosing(WindowEvent e) {
					cancel(true);
				}
			});

			// poll progress state (cheap and avoids flooding the EDT)
			Timer refresh = new Timer(100, evt -> {
				messageLabel.setText(state.message == null || state.message.isEmpty() ? " " : state.message);
				if (state.total > 0) {
					double fraction = Math.min(1, (double) state.done / state.total);
					progressBar.setIndeterminate(false);
					progressBar.setMaximum(1000);
					progressBar.setValue((int) (1000 * fraction));
					remainingLabel.setText(getRemainingTime(System.currentTimeMillis() - start, fraction));
					taskbar.setProgress(fraction);
				} else {
					taskbar.setIndeterminate();
				}
			});
			refresh.start();
			d.addWindowListener(new WindowAdapter() {

				@Override
				public void windowClosed(WindowEvent e) {
					refresh.stop();
					taskbar.clear();
				}
			});

			d.pack();
			d.setResizable(false);
			d.setLocationRelativeTo(owner);
			d.setAlwaysOnTop(owner == null);
			return d;
		}
	}

	/**
	 * e.g. About 2 minutes left (only after a few seconds, early estimates are not meaningful)
	 */
	private static String getRemainingTime(long elapsed, double fraction) {
		if (elapsed < 3000 || fraction <= 0.02 || fraction >= 1) {
			return " ";
		}

		long seconds = Math.round(elapsed * (1 - fraction) / fraction / 1000);
		if (seconds < 60) {
			return "Less than a minute left";
		}
		long minutes = Math.round(seconds / 60.0);
		if (minutes < 60) {
			return minutes == 1 ? "About 1 minute left" : String.format("About %d minutes left", minutes);
		}
		return String.format("About %.1f hours left", minutes / 60.0);
	}

	/**
	 * Progress in the Windows taskbar button or the macOS dock icon (if supported)
	 */
	private static class TaskbarProgress {

		private final Window window;
		private final Taskbar taskbar;

		public TaskbarProgress(Window owner) {
			this.window = owner instanceof Frame ? owner : owner != null ? SwingUtilities.getWindowAncestor(owner) : null;
			this.taskbar = Taskbar.isTaskbarSupported() ? Taskbar.getTaskbar() : null;
		}

		public void setProgress(double fraction) {
			int value = (int) Math.round(100 * fraction);
			try {
				if (taskbar != null && window != null && taskbar.isSupported(Taskbar.Feature.PROGRESS_STATE_WINDOW)) {
					taskbar.setWindowProgressState(window, Taskbar.State.NORMAL);
					taskbar.setWindowProgressValue(window, value);
				} else if (taskbar != null && taskbar.isSupported(Taskbar.Feature.PROGRESS_VALUE)) {
					taskbar.setProgressValue(value);
				}
			} catch (Exception e) {
				debug.finest(e::toString);
			}
		}

		public void setIndeterminate() {
			try {
				if (taskbar != null && window != null && taskbar.isSupported(Taskbar.Feature.PROGRESS_STATE_WINDOW)) {
					taskbar.setWindowProgressState(window, Taskbar.State.INDETERMINATE);
				}
			} catch (Exception e) {
				debug.finest(e::toString);
			}
		}

		public void clear() {
			try {
				if (taskbar != null && window != null && taskbar.isSupported(Taskbar.Feature.PROGRESS_STATE_WINDOW)) {
					taskbar.setWindowProgressState(window, Taskbar.State.OFF);
				} else if (taskbar != null && taskbar.isSupported(Taskbar.Feature.PROGRESS_VALUE)) {
					taskbar.setProgressValue(-1);
				}
			} catch (Exception e) {
				debug.finest(e::toString);
			}
		}
	}

	private static class ProgressState {

		private volatile String message;
		private volatile long done;
		private volatile long total;

		private FutureTask<?> task;

		public void setMessage(String message) {
			this.message = message;
		}

		public void setProgress(Long done, Long total) {
			this.done = done;
			this.total = total;
		}

		public boolean isCancelled() {
			return task.isCancelled();
		}
	}

	private ProgressMonitor() {
		throw new UnsupportedOperationException();
	}

}
