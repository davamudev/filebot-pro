package dev.davamu.filebotpro.ui.subtitle;

import static java.util.stream.Collectors.*;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.Transferable;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.TransferHandler;

import dev.davamu.filebotpro.ui.transfer.ByteBufferTransferable;
import dev.davamu.filebotpro.ui.transfer.ClipboardHandler;
import dev.davamu.filebotpro.ui.transfer.TransferableExportHandler;
import dev.davamu.filebotpro.vfs.MemoryFile;

class MemoryFileListExportHandler implements TransferableExportHandler, ClipboardHandler {

	public boolean canExport(JComponent component) {
		JList<?> list = (JList<?>) component;

		// can't export anything, if nothing is selected
		return !list.isSelectionEmpty();
	}

	public List<MemoryFile> export(JComponent component) {
		JList<?> list = (JList<?>) component;

		// get selected values as list
		return list.getSelectedValuesList().stream().map(MemoryFile.class::cast).collect(toList());
	}

	@Override
	public int getSourceActions(JComponent component) {
		return canExport(component) ? TransferHandler.COPY_OR_MOVE : TransferHandler.NONE;
	}

	@Override
	public Transferable createTransferable(JComponent component) {
		Map<String, ByteBuffer> vfs = new LinkedHashMap<String, ByteBuffer>();

		for (MemoryFile file : export(component)) {
			vfs.put(file.getName(), file.getData());
		}

		return new ByteBufferTransferable(vfs);
	}

	@Override
	public void exportToClipboard(JComponent component, Clipboard clip, int action) {
		clip.setContents(createTransferable(component), null);
	}

	@Override
	public void exportDone(JComponent source, Transferable data, int action) {

	}

}
