package dev.davamu.filebotpro.platform.windows;

import static javax.swing.BorderFactory.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.ui.ThemeSupport.*;

import java.util.logging.Level;

import javax.swing.UIManager;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.W32Errors;
import com.sun.jna.platform.win32.WTypes.LPWSTR;
import com.sun.jna.platform.win32.WinDef.UINT;
import com.sun.jna.platform.win32.WinDef.UINTByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

public class WinAppUtilities {

	public static void setAppUserModelID(String aumid) {
		try {
			Shell32.INSTANCE.SetCurrentProcessExplicitAppUserModelID(new WString(aumid));
		} catch (Throwable t) {
			debug.log(Level.WARNING, t.getMessage(), t);
		}
	}

	public static String getPackageName() {
		UINTByReference packageFullNameLength = new UINTByReference(new UINT(64));
		LPWSTR packageFullName = new LPWSTR(new Memory(packageFullNameLength.getValue().intValue() * Native.WCHAR_SIZE));

		NativeLong r = Kernel32.INSTANCE.GetCurrentPackageFullName(packageFullNameLength, packageFullName);

		if (r.intValue() != W32Errors.ERROR_SUCCESS) {
			throw new IllegalStateException(String.format("Kernel32.GetCurrentPackageFullName (%s)", r));
		}

		return packageFullName.getValue();
	}

	/**
	 * Let another process take the foreground (Windows only lets the process the user is interacting with do that)
	 */
	public static void allowSetForegroundWindow(int pid) {
		try {
			User32.INSTANCE.AllowSetForegroundWindow(pid);
		} catch (Throwable t) {
			debug.log(Level.WARNING, t.getMessage(), t);
		}
	}

	private interface User32 extends StdCallLibrary {

		User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);

		boolean AllowSetForegroundWindow(int dwProcessId);

	}

	public static void initializeApplication(String aumid) {
		if (aumid != null) {
			setAppUserModelID(aumid);
		}
	}

	private WinAppUtilities() {
		throw new UnsupportedOperationException();
	}

}
