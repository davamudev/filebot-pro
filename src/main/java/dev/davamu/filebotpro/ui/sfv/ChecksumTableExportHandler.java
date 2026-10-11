
package dev.davamu.filebotpro.ui.sfv;

import static java.nio.charset.StandardCharsets.*;
import static dev.davamu.filebotpro.Logging.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import dev.davamu.filebotpro.hash.HashType;
import dev.davamu.filebotpro.hash.VerificationFileWriter;
import dev.davamu.filebotpro.ui.transfer.TextFileExportHandler;
import dev.davamu.filebotpro.util.FileUtilities;

class ChecksumTableExportHandler extends TextFileExportHandler {

	private final ChecksumTableModel model;

	public ChecksumTableExportHandler(ChecksumTableModel model) {
		this.model = model;
	}

	@Override
	public boolean canExport() {
		return model.getRowCount() > 0 && defaultColumn() != null;
	}

	@Override
	public void export(PrintWriter out) {
		export(new VerificationFileWriter(out, model.getHashType().getFormat(), UTF_8), defaultColumn(), model.getHashType(), null);
	}

	@Override
	public String getDefaultFileName() {
		return getDefaultFileName(defaultColumn());
	}

	protected File defaultColumn() {
		// select first column that is not a verification file column
		for (File root : model.getChecksumColumns()) {
			if (root.isDirectory())
				return root;
		}

		return null;
	}

	public void export(File file, File column) throws IOException {
		VerificationFileWriter writer = new VerificationFileWriter(file, model.getHashType().getFormat(), UTF_8);
		int skipped;

		try {
			// paths must be relative to the folder of the verification file, which is not necessarily the column root
			skipped = export(writer, column, model.getHashType(), file.getAbsoluteFile().getParentFile());
		} finally {
			writer.close();
		}

		if (skipped > 0) {
			log.warning(String.format("%d files were not exported because their %s hash is not computed yet: %s", skipped, model.getHashType(), file));
		}
	}

	/**
	 * @return number of files that were skipped because their hash is not available
	 */
	public int export(VerificationFileWriter out, File column, HashType type, File folder) {
		int skipped = 0;

		for (ChecksumRow row : model.rows()) {
			ChecksumCell cell = row.getChecksum(column);

			if (cell != null) {
				String hash = cell.getChecksum(type);

				if (hash != null) {
					out.write(folder == null ? cell.getName() : VerificationFileWriter.getRelativePath(new File(cell.getRoot(), cell.getName()), folder), hash);
				} else {
					skipped++;
				}
			}
		}

		return skipped;
	}

	public String getDefaultFileName(File column) {
		StringBuilder sb = new StringBuilder();

		// append file name
		sb.append(column != null ? FileUtilities.getName(column) : "name");

		// append file extension
		sb.append('.').append(model.getHashType().name().toLowerCase());

		return sb.toString();
	}
}
