package dev.davamu.filebotpro.platform.windows;

import static dev.davamu.filebotpro.Logging.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HMODULE;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinUser.WNDCLASSEX;
import com.sun.jna.platform.win32.WinUser.WindowProc;
import com.sun.jna.win32.StdCallLibrary;

/**
 * Handles WM_QUERYENDSESSION and WM_ENDSESSION (i.e. Windows log off, restart or shut down) via a hidden window: Windows shows that
 * FileBot Pro is busy while files are being renamed, and the application shuts down orderly (rename history, caches and preferences are
 * saved) instead of being killed.
 */
public final class SessionEndHandler {

	private static final int WM_QUERYENDSESSION = 0x0011;
	private static final int WM_ENDSESSION = 0x0016;
	private static final int WM_APP_BLOCK = 0x8000 + 1;
	private static final int WM_APP_UNBLOCK = 0x8000 + 2;

	private static final String WINDOW_CLASS = "FileBotProSessionEndHandler";

	private static final AtomicInteger busy = new AtomicInteger(0);

	private static volatile HWND window;
	private static volatile String reason = "Renaming files";
	private static volatile Runnable endSessionHandler;

	// keep a reference to the callback, so that it is not garbage collected while the window exists
	private static WindowProc windowProc;

	/**
	 * Start listening for session end events (only on Windows)
	 *
	 * @param handler
	 *            called when the Windows session ends (e.g. save state and exit), or null
	 */
	public static synchronized void install(Runnable handler) {
		endSessionHandler = handler;

		if (!Platform.isWindows() || windowProc != null) {
			return;
		}

		CountDownLatch ready = new CountDownLatch(1);
		Thread thread = new Thread(() -> run(ready), "SessionEndHandler");
		thread.setDaemon(true);
		thread.start();

		try {
			ready.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Tell Windows that the application must not be shut down right now (e.g. while files are being moved)
	 *
	 * @return close to allow shutting down again
	 */
	public static Block block(String message) {
		if (!Platform.isWindows()) {
			return () -> {
			};
		}

		install(endSessionHandler);
		reason = message;
		if (busy.incrementAndGet() == 1) {
			post(WM_APP_BLOCK);
		}

		AtomicInteger closed = new AtomicInteger(0);
		return () -> {
			if (closed.getAndIncrement() == 0 && busy.decrementAndGet() == 0) {
				post(WM_APP_UNBLOCK);
			}
		};
	}

	private static void post(int message) {
		HWND hwnd = window;
		if (hwnd != null) {
			User32.INSTANCE.PostMessage(hwnd, message, new WPARAM(0), new LPARAM(0));
		}
	}

	private static void run(CountDownLatch ready) {
		try {
			User32 user32 = User32.INSTANCE;
			HMODULE instance = Kernel32.INSTANCE.GetModuleHandle(null);

			windowProc = SessionEndHandler::windowProc;

			WNDCLASSEX windowClass = new WNDCLASSEX();
			windowClass.hInstance = instance;
			windowClass.lpfnWndProc = windowProc;
			windowClass.lpszClassName = WINDOW_CLASS;
			user32.RegisterClassEx(windowClass);

			// hidden top-level window (message-only windows do not receive WM_QUERYENDSESSION)
			window = user32.CreateWindowEx(0, WINDOW_CLASS, "FileBot Pro", 0, 0, 0, 0, 0, null, null, instance, null);
			if (window == null) {
				debug.warning("CreateWindowEx failed: " + Native.getLastError());
				return;
			}
		} catch (Throwable e) {
			debug.warning(cause("Failed to register session end handler", e));
			return;
		} finally {
			ready.countDown();
		}

		// pump messages for the hidden window
		MSG msg = new MSG();
		while (User32.INSTANCE.GetMessage(msg, null, 0, 0) > 0) {
			User32.INSTANCE.TranslateMessage(msg);
			User32.INSTANCE.DispatchMessage(msg);
		}
	}

	private static LRESULT windowProc(HWND hwnd, int message, WPARAM wParam, LPARAM lParam) {
		switch (message) {
		case WM_APP_BLOCK:
			ShutdownBlock.INSTANCE.ShutdownBlockReasonCreate(hwnd, new WString(reason));
			return new LRESULT(0);
		case WM_APP_UNBLOCK:
			ShutdownBlock.INSTANCE.ShutdownBlockReasonDestroy(hwnd);
			return new LRESULT(0);
		case WM_QUERYENDSESSION:
			// FALSE = ask the user to wait until the files are renamed (Windows shows the shutdown block reason)
			debug.fine(() -> "WM_QUERYENDSESSION (busy: " + busy.get() + ")");
			return new LRESULT(busy.get() > 0 ? 0 : 1);
		case WM_ENDSESSION:
			// the session ends now if wParam is TRUE (and we are terminated as soon as we return)
			if (wParam.longValue() != 0) {
				debug.fine("WM_ENDSESSION");
				Runnable handler = endSessionHandler;
				if (handler != null) {
					try {
						handler.run();
					} catch (Throwable e) {
						debug.warning(cause("Session end handler failed", e));
					}
				}
			}
			return new LRESULT(0);
		default:
			return User32.INSTANCE.DefWindowProc(hwnd, message, wParam, lParam);
		}
	}

	public interface Block extends AutoCloseable {

		@Override
		void close();
	}

	private interface ShutdownBlock extends StdCallLibrary {

		ShutdownBlock INSTANCE = Native.load("user32", ShutdownBlock.class);

		// see https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-shutdownblockreasoncreate
		boolean ShutdownBlockReasonCreate(HWND hwnd, WString reason);

		boolean ShutdownBlockReasonDestroy(HWND hwnd);
	}

	private SessionEndHandler() {
		throw new UnsupportedOperationException();
	}

}
