package dev.davamu.filebotpro;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.similarity.Normalization.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Level;

import javax.swing.JFileChooser;

import dev.davamu.filebotpro.platform.linux.ExternalFileDialog;
import dev.davamu.filebotpro.platform.mac.MacAppUtilities;
import dev.davamu.filebotpro.platform.windows.WinFileDialog;
import dev.davamu.filebotpro.util.FileUtilities;
import dev.davamu.filebotpro.util.FileUtilities.ExtensionFileFilter;

public class UserFiles {

	public static void trash(File file) throws IOException {
		// use system trash if possible
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
			try {
				if (Desktop.getDesktop().moveToTrash(file)) {
					return;
				}
				debug.log(Level.WARNING, message("Failed to move file to trash", file));
			} catch (Exception e) {
				debug.log(Level.WARNING, e::toString);
			}
		}

		// delete permanently if necessary
		if (file.exists()) {
			FileUtilities.delete(file);
		}
	}

	public static void revealFiles(Collection<File> files) {
		// try to reveal file in folder
		if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
			files.stream().collect(groupingBy(File::getParentFile, LinkedHashMap::new, toList())).forEach((parent, children) -> {
				try {
					Desktop.getDesktop().browseFileDirectory(children.get(children.size() - 1));
				} catch (Exception e) {
					debug.log(Level.WARNING, e::toString);
				}
			});
			return;
		}

		// if we can't reveal the file in folder, just reveal the parent folder
		files.stream().map(it -> it.getParentFile()).distinct().forEach(it -> {
			try {
				Desktop.getDesktop().open(it);
			} catch (Exception e) {
				debug.log(Level.WARNING, e::toString);
			}
		});
	}

	private static FileChooser defaultFileChooser = getPreferredFileChooser();

	public static void setDefaultFileChooser(FileChooser fileChooser) {
		defaultFileChooser = fileChooser;
	}

	public static List<File> showLoadDialogSelectFiles(boolean folderMode, boolean multiSelection, File defaultFile, ExtensionFileFilter filter, String title, ActionEvent evt) {
		String defaultFileKey = ((folderMode && filter == null) || !(folderMode && filter != null && isShiftOrAltDown(evt))) ? KEY_OPEN_FOLDER : KEY_OPEN_FILE;
		List<File> files = defaultFileChooser.showLoadDialogSelectFiles(defaultFileKey == KEY_OPEN_FOLDER, multiSelection, getFileChooserDefaultFile(defaultFileKey, defaultFile), filter, title, evt);
		if (files.size() > 0) {
			setFileChooserDefaultFile(defaultFileKey, files.get(0));
		}
		return files;
	}

	public static File showSaveDialogSelectFile(boolean folderMode, File defaultFile, String title, ActionEvent evt) {
		File file = defaultFileChooser.showSaveDialogSelectFile(folderMode, getFileChooserDefaultFile(KEY_SAVE, defaultFile), title, evt);
		if (file != null) {
			setFileChooserDefaultFile(KEY_SAVE, file);
		}
		return file;
	}

	public static File showOpenDialogSelectFolder(File defaultFile, String title, ActionEvent evt) {
		List<File> folder = defaultFileChooser.showLoadDialogSelectFiles(true, false, defaultFile, null, title, evt);
		return folder.size() > 0 ? folder.get(0) : null;
	}

	private static final String PREF_KEY_PREFIX = "dialog.";
	private static final String KEY_OPEN_FILE = "open.file";
	private static final String KEY_OPEN_FOLDER = "open.folder";
	private static final String KEY_SAVE = "save.file";

	protected static File getFileChooserDefaultFile(String key, File userValue) {
		if (userValue != null && userValue.getParentFile() != null)
			return userValue;

		String path = Settings.forPackage(UserFiles.class).get(PREF_KEY_PREFIX + key);
		if (path == null || path.isEmpty())
			return userValue;

		if (userValue != null && userValue.getParentFile() == null)
			return new File(new File(path).getParentFile(), userValue.getName());

		return new File(path);
	}

	protected static void setFileChooserDefaultFile(String name, File file) {
		Settings.forPackage(UserFiles.class).put(PREF_KEY_PREFIX + name, file.getPath());
	}

	public enum FileChooser {

		Swing {

			@Override
			public List<File> showLoadDialogSelectFiles(boolean folderMode, boolean multiSelection, File defaultFile, ExtensionFileFilter filter, String title, ActionEvent evt) {
				JFileChooser chooser = new JFileChooser();
				chooser.setDialogTitle(title);
				chooser.setMultiSelectionEnabled(multiSelection);
				chooser.setFileSelectionMode(folderMode && filter == null ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_AND_DIRECTORIES);

				if (defaultFile != null) {
					if (defaultFile.isFile()) {
						chooser.setSelectedFile(defaultFile);
					} else if (defaultFile.getParentFile() != null && defaultFile.getParentFile().isDirectory()) {
						chooser.setCurrentDirectory(defaultFile.getParentFile());
					}
				}

				if (filter != null && !filter.acceptAny()) {
					chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(truncateText(filter.toString(), 80), filter.extensions()));
				}

				if (chooser.showOpenDialog(getWindow(evt.getSource())) == JFileChooser.APPROVE_OPTION) {
					if (chooser.getSelectedFiles().length > 0)
						return asList(chooser.getSelectedFiles());
					if (chooser.getSelectedFile() != null)
						return asList(chooser.getSelectedFile());
				}
				return asList(new File[0]);
			}

			@Override
			public File showSaveDialogSelectFile(boolean folderMode, File defaultFile, String title, ActionEvent evt) {
				JFileChooser chooser = new JFileChooser();
				chooser.setDialogTitle(title);
				chooser.setMultiSelectionEnabled(false);
				chooser.setFileSelectionMode(folderMode ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_AND_DIRECTORIES);
				chooser.setSelectedFile(defaultFile);

				if (chooser.showSaveDialog(getWindow(evt.getSource())) != JFileChooser.APPROVE_OPTION) {
					return null;
				}
				return chooser.getSelectedFile();
			}
		},

		AWT {

			@Override
			public List<File> showLoadDialogSelectFiles(boolean folderMode, boolean multiSelection, File defaultFile, ExtensionFileFilter filter, String title, ActionEvent evt) {
				FileDialog fileDialog = createFileDialog(evt, title, FileDialog.LOAD, folderMode);
				fileDialog.setTitle(title);
				fileDialog.setMultipleMode(multiSelection);

				if (defaultFile != null) {
					if (folderMode && defaultFile.isDirectory()) {
						fileDialog.setDirectory(defaultFile.getPath());
					} else if (defaultFile.getParentFile() != null && defaultFile.getParentFile().isDirectory()) {
						fileDialog.setDirectory(defaultFile.getParentFile().getPath());
						fileDialog.setFile(defaultFile.getName());
					}
				}

				if (filter != null) {
					fileDialog.setFilenameFilter(filter);
				}

				fileDialog.setVisible(true);
				return asList(fileDialog.getFiles());
			}

			@Override
			public File showSaveDialogSelectFile(boolean folderMode, File defaultFile, String title, ActionEvent evt) {
				FileDialog fileDialog = createFileDialog(evt, title, FileDialog.SAVE, folderMode);
				fileDialog.setTitle(title);
				fileDialog.setMultipleMode(false);
				if (defaultFile != null) {
					if (defaultFile.getParentFile() != null && defaultFile.getParentFile().isDirectory()) {
						fileDialog.setDirectory(defaultFile.getParentFile().getPath());
					}
					fileDialog.setFile(defaultFile.getName());
				}

				fileDialog.setVisible(true);
				File[] files = fileDialog.getFiles();
				return files.length > 0 ? files[0] : null;
			}

			public FileDialog createFileDialog(ActionEvent evt, String title, int mode, boolean fileDialogForDirectories) {
				// By default, the AWT File Dialog lets you choose a file. Under certain circumstances, however, it may be proper for you to choose a directory instead. If that is the case, set this property to allow for directory selection in a file dialog.
				System.setProperty("apple.awt.fileDialogForDirectories", String.valueOf(fileDialogForDirectories));

				if (evt.getSource() instanceof Frame) {
					return new FileDialog((Frame) evt.getSource(), title, mode);
				}
				if (evt.getSource() instanceof Dialog) {
					return new FileDialog((Dialog) evt.getSource(), title, mode);
				}

				Frame[] frames = Frame.getFrames();
				return new FileDialog(frames.length > 0 ? frames[0] : null, title, mode);
			}
		},

		Windows {

			@Override
			public List<File> showLoadDialogSelectFiles(boolean folderMode, boolean multiSelection, File defaultFile, ExtensionFileFilter filter, String title, ActionEvent evt) {
				try {
					// the Windows dialog selects either files or folders, so files and folders mode selects folders (hold Shift or Alt to select files)
					String[] extensions = filter == null || filter.acceptAny() ? null : filter.extensions();
					String filterName = filter == null ? null : truncateText(filter.toString(), 80);
					return WinFileDialog.showOpenDialog(getWindow(evt.getSource()), title, folderMode, multiSelection, defaultFile, filterName, extensions);
				} catch (Throwable e) {
					debug.log(Level.WARNING, e, e::toString);
					return Swing.showLoadDialogSelectFiles(folderMode, multiSelection, defaultFile, filter, title, evt);
				}
			}

			@Override
			public File showSaveDialogSelectFile(boolean folderMode, File defaultFile, String title, ActionEvent evt) {
				try {
					return WinFileDialog.showSaveDialog(getWindow(evt.getSource()), title, folderMode, defaultFile);
				} catch (Throwable e) {
					debug.log(Level.WARNING, e, e::toString);
					return Swing.showSaveDialogSelectFile(folderMode, defaultFile, title, evt);
				}
			}
		},

		Linux {

			@Override
			public List<File> showLoadDialogSelectFiles(boolean folderMode, boolean multiSelection, File defaultFile, ExtensionFileFilter filter, String title, ActionEvent evt) {
				ExternalFileDialog dialog = ExternalFileDialog.getAvailable();
				if (dialog != null) {
					try {
						// zenity / kdialog select either files or folders, so files and folders mode selects folders (hold Shift or Alt to select files)
						String[] extensions = filter == null || filter.acceptAny() ? null : filter.extensions();
						String filterName = filter == null ? null : truncateText(filter.toString(), 80);

						// keep the user interface responsive while the external dialog is open
						return onSecondaryLoop(() -> dialog.showOpenDialog(title, folderMode, multiSelection, defaultFile, filterName, extensions));
					} catch (Throwable e) {
						debug.log(Level.WARNING, e, e::toString);
					}
				}
				return Swing.showLoadDialogSelectFiles(folderMode, multiSelection, defaultFile, filter, title, evt);
			}

			@Override
			public File showSaveDialogSelectFile(boolean folderMode, File defaultFile, String title, ActionEvent evt) {
				ExternalFileDialog dialog = ExternalFileDialog.getAvailable();
				if (dialog != null) {
					try {
						return onSecondaryLoop(() -> dialog.showSaveDialog(title, folderMode, defaultFile));
					} catch (Throwable e) {
						debug.log(Level.WARNING, e, e::toString);
					}
				}
				return Swing.showSaveDialogSelectFile(folderMode, defaultFile, title, evt);
			}
		},

		COCOA {

			@Override
			public List<File> showLoadDialogSelectFiles(boolean folderMode, boolean multiSelection, File defaultFile, ExtensionFileFilter filter, String title, ActionEvent evt) {
				// directly use NSOpenPanel for via Objective-C bridge for FILES_AND_DIRECTORIES mode
				if (folderMode && filter != null) {
					// call native NSOpenPanel openPanel via Objective-C bridge
					return MacAppUtilities.NSOpenPanel_openPanel_runModal(title, true, true, true, filter.acceptAny() ? null : filter.extensions());
				}

				// default to AWT implementation
				return AWT.showLoadDialogSelectFiles(folderMode, multiSelection, defaultFile, filter, title, evt);
			}

			@Override
			public File showSaveDialogSelectFile(boolean folderMode, File defaultFile, String title, ActionEvent evt) {
				// default to AWT implementation
				return AWT.showSaveDialogSelectFile(folderMode, defaultFile, title, evt);
			}
		};

		public abstract List<File> showLoadDialogSelectFiles(boolean folderMode, boolean multiSelection, File defaultFile, ExtensionFileFilter filter, String title, ActionEvent evt);

		public abstract File showSaveDialogSelectFile(boolean folderMode, File defaultFile, String title, ActionEvent evt);

	}

}
