package dev.davamu.filebotpro.platform.windows;

import static dev.davamu.filebotpro.Logging.*;

import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.Window;
import java.io.File;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid.CLSID;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Guid.IID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * Native Windows file dialog (IFileOpenDialog and IFileSaveDialog via COM), the same dialog as the Windows Explorer
 * with folder selection, recent places and the system theme (dark mode included).
 *
 * The dialog runs on its own COM thread while the Event Dispatch Thread keeps painting in a secondary loop, and it is
 * modal to the given owner window.
 */
public class WinFileDialog {

	private static final CLSID CLSID_FileOpenDialog = new CLSID("{DC1C5A9C-E88A-4dde-A5A1-60F82A20AEF7}");
	private static final CLSID CLSID_FileSaveDialog = new CLSID("{C0B4E2F3-BA21-4773-8DBA-335EC946EB8B}");
	private static final IID IID_IFileOpenDialog = new IID("{d57c7288-d4ad-4768-be02-9d969532d960}");
	private static final IID IID_IFileSaveDialog = new IID("{84bccd23-5fde-4cdb-aea4-af64b83d78ab}");
	private static final IID IID_IShellItem = new IID("{43826d1e-e718-42ee-bc55-a1e261c37bfe}");

	// FILEOPENDIALOGOPTIONS
	private static final int FOS_OVERWRITEPROMPT = 0x2;
	private static final int FOS_PICKFOLDERS = 0x20;
	private static final int FOS_FORCEFILESYSTEM = 0x40;
	private static final int FOS_ALLOWMULTISELECT = 0x200;
	private static final int FOS_PATHMUSTEXIST = 0x800;
	private static final int FOS_FILEMUSTEXIST = 0x1000;

	private static final int SIGDN_FILESYSPATH = 0x80058000;
	private static final int ERROR_CANCELLED = 0x800704C7;

	private static final int COINIT_APARTMENTTHREADED = 0x2;
	private static final int COINIT_DISABLE_OLE1DDE = 0x4;

	// IUnknown
	private static final int RELEASE = 2;

	// IModalWindow and IFileDialog
	private static final int SHOW = 3;
	private static final int SET_FILE_TYPES = 4;
	private static final int SET_OPTIONS = 9;
	private static final int GET_OPTIONS = 10;
	private static final int SET_FOLDER = 12;
	private static final int SET_FILE_NAME = 15;
	private static final int SET_TITLE = 17;
	private static final int GET_RESULT = 20;

	// IFileOpenDialog
	private static final int GET_RESULTS = 27;

	// IShellItem
	private static final int GET_DISPLAY_NAME = 5;

	// IShellItemArray
	private static final int GET_COUNT = 7;
	private static final int GET_ITEM_AT = 8;

	private interface Shell32 extends StdCallLibrary {

		Shell32 INSTANCE = Native.load("shell32", Shell32.class, W32APIOptions.DEFAULT_OPTIONS);

		HRESULT SHCreateItemFromParsingName(WString path, Pointer bindContext, GUID riid, PointerByReference item);
	}

	/**
	 * @param folderMode
	 *            select folders instead of files
	 * @param extensions
	 *            file name filter (e.g. mkv, mp4), or null for all files
	 * @return selected files, or an empty list if the dialog was cancelled
	 */
	public static List<File> showOpenDialog(Window owner, String title, boolean folderMode, boolean multiSelection, File defaultFile, String filterName, String[] extensions) throws Exception {
		int options = FOS_FORCEFILESYSTEM | FOS_PATHMUSTEXIST | FOS_FILEMUSTEXIST | (folderMode ? FOS_PICKFOLDERS : 0) | (multiSelection ? FOS_ALLOWMULTISELECT : 0);
		return showDialog(owner, () -> {
			Pointer dialog = create(CLSID_FileOpenDialog, IID_IFileOpenDialog);
			try {
				Object fileTypes = configure(dialog, title, options, defaultFile, !folderMode, folderMode ? null : filterName, folderMode ? null : extensions);
				boolean approved = show(dialog, owner);
				Reference.reachabilityFence(fileTypes);
				if (!approved) {
					return new ArrayList<File>();
				}

				PointerByReference results = new PointerByReference();
				check(call(dialog, GET_RESULTS, results));
				try {
					IntByReference count = new IntByReference();
					check(call(results.getValue(), GET_COUNT, count));

					List<File> files = new ArrayList<File>(count.getValue());
					for (int i = 0; i < count.getValue(); i++) {
						PointerByReference item = new PointerByReference();
						check(call(results.getValue(), GET_ITEM_AT, i, item));
						files.add(getPath(item.getValue()));
					}
					return files;
				} finally {
					release(results.getValue());
				}
			} finally {
				release(dialog);
			}
		});
	}

	/**
	 * @return selected file, or null if the dialog was cancelled
	 */
	public static File showSaveDialog(Window owner, String title, boolean folderMode, File defaultFile) throws Exception {
		if (folderMode) {
			List<File> folder = showOpenDialog(owner, title, true, false, defaultFile, null, null);
			return folder.isEmpty() ? null : folder.get(0);
		}

		int options = FOS_FORCEFILESYSTEM | FOS_PATHMUSTEXIST | FOS_OVERWRITEPROMPT;
		return showDialog(owner, () -> {
			Pointer dialog = create(CLSID_FileSaveDialog, IID_IFileSaveDialog);
			try {
				configure(dialog, title, options, defaultFile, true, null, null);
				if (!show(dialog, owner)) {
					return null;
				}

				PointerByReference item = new PointerByReference();
				check(call(dialog, GET_RESULT, item));
				return getPath(item.getValue());
			} finally {
				release(dialog);
			}
		});
	}

	@FunctionalInterface
	private interface DialogTask<T> {
		T run() throws Exception;
	}

	/**
	 * Run the dialog on a new COM thread and keep the EDT alive until the dialog is closed
	 */
	private static <T> T showDialog(Window owner, DialogTask<T> task) throws Exception {
		AtomicReference<T> result = new AtomicReference<T>();
		AtomicReference<Exception> error = new AtomicReference<Exception>();

		SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
		Thread thread = new Thread(() -> {
			try {
				HRESULT init = Ole32.INSTANCE.CoInitializeEx(null, COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE);
				try {
					result.set(task.run());
				} finally {
					if (init.intValue() >= 0) {
						Ole32.INSTANCE.CoUninitialize();
					}
				}
			} catch (Exception e) {
				error.set(e);
			} finally {
				loop.exit();
			}
		}, "WinFileDialog");
		thread.setDaemon(true);
		thread.start();

		if (!loop.enter()) {
			thread.join();
		}

		if (error.get() != null) {
			throw error.get();
		}
		return result.get();
	}

	private static Pointer create(CLSID clsid, IID iid) throws Exception {
		PointerByReference dialog = new PointerByReference();
		check(Ole32.INSTANCE.CoCreateInstance(clsid, null, WTypes.CLSCTX_INPROC_SERVER, iid, dialog).intValue());
		return dialog.getValue();
	}

	/**
	 * @return native memory that must stay reachable until the dialog is closed
	 */
	private static Object configure(Pointer dialog, String title, int options, File defaultFile, boolean selectFile, String filterName, String[] extensions) throws Exception {
		IntByReference current = new IntByReference();
		check(call(dialog, GET_OPTIONS, current));
		check(call(dialog, SET_OPTIONS, current.getValue() | options));

		if (title != null) {
			check(call(dialog, SET_TITLE, new WString(title)));
		}

		Object fileTypes = null;
		if (extensions != null && extensions.length > 0) {
			fileTypes = setFileTypes(dialog, filterName, extensions);
		}

		if (defaultFile != null) {
			// Windows shell APIs reject relative paths and . or .. segments
			File file = defaultFile.toPath().toAbsolutePath().normalize().toFile();
			File folder = file.isDirectory() ? file : file.getParentFile();

			if (folder != null && folder.isDirectory()) {
				PointerByReference item = new PointerByReference();
				if (Shell32.INSTANCE.SHCreateItemFromParsingName(new WString(folder.getPath()), null, IID_IShellItem, item).intValue() >= 0) {
					try {
						call(dialog, SET_FOLDER, item.getValue());
					} finally {
						release(item.getValue());
					}
				}
			}

			if (selectFile && !file.isDirectory() && !file.getName().isEmpty()) {
				call(dialog, SET_FILE_NAME, new WString(file.getName()));
			}
		}

		return fileTypes;
	}

	/**
	 * @return native memory that must stay reachable until the dialog is closed
	 */
	private static Object setFileTypes(Pointer dialog, String filterName, String[] extensions) throws Exception {
		String[][] specs = { { filterName != null ? filterName : "Supported Files", Stream.of(extensions).map(it -> "*." + it).reduce((a, b) -> a + ";" + b).get() }, { "All Files", "*.*" } };

		// COMDLG_FILTERSPEC[] { LPCWSTR pszName; LPCWSTR pszSpec; }, strings must stay alive until the dialog is closed
		Memory array = new Memory((long) specs.length * 2 * Native.POINTER_SIZE);
		List<Memory> strings = new ArrayList<Memory>();
		for (int i = 0; i < specs.length; i++) {
			for (int j = 0; j < 2; j++) {
				Memory s = new Memory((specs[i][j].length() + 1L) * Native.WCHAR_SIZE);
				s.setWideString(0, specs[i][j]);
				strings.add(s);
				array.setPointer((long) (i * 2 + j) * Native.POINTER_SIZE, s);
			}
		}

		check(call(dialog, SET_FILE_TYPES, specs.length, array));

		strings.add(array);
		return strings;
	}

	private static boolean show(Pointer dialog, Window owner) throws Exception {
		Pointer hwnd = owner != null && owner.isDisplayable() ? Native.getWindowPointer(owner) : null;
		int hr = call(dialog, SHOW, hwnd);
		if (hr == ERROR_CANCELLED) {
			return false;
		}
		check(hr);
		return true;
	}

	private static File getPath(Pointer item) throws Exception {
		try {
			PointerByReference name = new PointerByReference();
			check(call(item, GET_DISPLAY_NAME, SIGDN_FILESYSPATH, name));
			try {
				return new File(name.getValue().getWideString(0));
			} finally {
				Ole32.INSTANCE.CoTaskMemFree(name.getValue());
			}
		} finally {
			release(item);
		}
	}

	private static int call(Pointer object, int method, Object... args) {
		Pointer function = object.getPointer(0).getPointer((long) method * Native.POINTER_SIZE);
		Object[] arguments = new Object[args.length + 1];
		arguments[0] = object;
		System.arraycopy(args, 0, arguments, 1, args.length);
		return Function.getFunction(function, Function.ALT_CONVENTION).invokeInt(arguments);
	}

	private static void release(Pointer object) {
		if (object != null) {
			try {
				call(object, RELEASE);
			} catch (Throwable e) {
				debug.finest(e::toString);
			}
		}
	}

	private static void check(int hr) throws Exception {
		if (hr < 0) {
			throw new IllegalStateException(String.format("HRESULT 0x%08X", hr));
		}
	}

	private WinFileDialog() {
		throw new UnsupportedOperationException();
	}

}
