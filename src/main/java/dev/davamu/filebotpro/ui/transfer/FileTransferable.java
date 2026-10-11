package dev.davamu.filebotpro.ui.transfer;

import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.InvalidDnDOperationException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;

import dev.davamu.filebotpro.platform.gnome.GVFS;

public class FileTransferable implements Transferable {

	public static final boolean forceSortOrder = Boolean.parseBoolean(System.getProperty("dev.davamu.filebotpro.dnd.sort"));

	public static final DataFlavor uriListFlavor = createUriListFlavor();

	private static DataFlavor createUriListFlavor() {
		try {
			return new DataFlavor("text/uri-list;class=java.nio.CharBuffer");
		} catch (ClassNotFoundException e) {
			// will never happen
			throw new RuntimeException(e);
		}
	}

	public static boolean isFileListFlavor(DataFlavor flavor) {
		return flavor.isFlavorJavaFileListType() || flavor.equals(uriListFlavor);
	}

	public static boolean hasFileListFlavor(Transferable tr) {
		return tr.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || tr.isDataFlavorSupported(FileTransferable.uriListFlavor);
	}

	private final File[] files;

	public FileTransferable(File... files) {
		this.files = files;
	}

	public FileTransferable(Collection<File> files) {
		this.files = files.toArray(new File[0]);
	}

	@Override
	public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
		if (flavor.isFlavorJavaFileListType())
			return Arrays.asList(files);
		else if (flavor.equals(uriListFlavor))
			return CharBuffer.wrap(getUriList());
		else
			throw new UnsupportedFlavorException(flavor);
	}

	/**
	 * @return line separated list of file URIs
	 */
	private String getUriList() {
		StringBuilder sb = new StringBuilder(80 * files.length);

		for (File file : files) {
			// use URI encoded path
			sb.append("file://").append(file.toURI().getRawPath());
			sb.append("\r\n");
		}

		return sb.toString();
	}

	@Override
	public DataFlavor[] getTransferDataFlavors() {
		return new DataFlavor[] { DataFlavor.javaFileListFlavor, uriListFlavor };
	}

	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor) {
		return isFileListFlavor(flavor);
	}

	public static List<File> getFilesFromTransferable(Transferable tr) throws IOException, UnsupportedFlavorException {
		// On Linux, if a file is dragged from a smb share to into a java application (e.g. Ubuntu Files to FileBot Pro)
		// the application/x-java-file-list transfer data will be an empty list
		// because the native uri-list flavor drop data will be a list of smb URLs (e.g. smb://10.0.0.1/data/file.txt)
		// and not local GVFS paths (e.g. /run/user/1000/gvfs/smb-share:server=10.0.01.1,share=data/file.txt)
		if (useGVFS() && tr.isDataFlavorSupported(FileTransferable.uriListFlavor)) {
			return getFilesFromUriList(tr, uri -> GVFS.getDefaultVFS().getPathForURI(uri));
		}

		// Windows / Mac and default handling
		if (tr.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
			// file list flavor
			List<File> files = getTransferData(tr, DataFlavor.javaFileListFlavor, List.class);

			if (files != null && (!files.isEmpty() || !tr.isDataFlavorSupported(FileTransferable.uriListFlavor))) {
				// Windows Explorer DnD / Selection Order is broken and will probably never be fixed,
				// so we provide an override for users that want to enforce alphanumeric sort order of files dragged in
				if (forceSortOrder) {
					return files.stream().sorted(HUMAN_NAME_ORDER).collect(toList());
				} else {
					return files;
				}
			}

			// on some platforms transferable data will not be available until the drop has been accepted
			if (files == null) {
				return null;
			}
		}

		// some file managers (e.g. KDE Dolphin) only provide a text/uri-list, or an empty application/x-java-file-list
		if (tr.isDataFlavorSupported(FileTransferable.uriListFlavor)) {
			return getFilesFromUriList(tr, FileTransferable::getFileForURI);
		}

		// cannot get files from transferable
		throw new UnsupportedFlavorException(DataFlavor.javaFileListFlavor);
	}

	private static List<File> getFilesFromUriList(Transferable tr, Function<String, File> resolver) throws IOException, UnsupportedFlavorException {
		Readable data = getTransferData(tr, FileTransferable.uriListFlavor, Readable.class);
		if (data == null) {
			return null;
		}

		try (Scanner scanner = new Scanner(data)) {
			List<File> files = new ArrayList<File>();

			while (scanner.hasNextLine()) {
				// ignore trailing NUL characters and whitespace (e.g. KDE Dolphin)
				String line = scanner.nextLine().replace("\0", "").trim();

				if (line.isEmpty() || line.startsWith("#")) {
					// the line is empty or a comment (as per RFC 2483)
					continue;
				}

				try {
					File file = resolver.apply(line);

					if (file == null || !file.exists()) {
						throw new FileNotFoundException(line);
					}

					files.add(file);
				} catch (Throwable e) {
					debug.warning(format("URI: %s => %s", line, e));
				}
			}

			return files;
		}
	}

	private static File getFileForURI(String uri) {
		// KDE Dolphin does not encode [ and ] in file URIs
		return new File(URI.create(uri.replace("[", "%5B").replace("]", "%5D")));
	}

	private static <T> T getTransferData(Transferable tr, DataFlavor flavor, Class<T> type) throws IOException, UnsupportedFlavorException, InvalidDnDOperationException {
		Object transferData;
		try {
			transferData = tr.getTransferData(flavor);
		} catch (IOException e) {
			// java.io.IOException: Owner failed to convert data
			throw new InvalidDnDOperationException(e.getMessage());
		}

		if (transferData != null && type.isInstance(transferData)) {
			return type.cast(transferData);
		}

		return null;
	}

}
