package dev.davamu.filebotpro.ui.filter;

import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashSet;

import javax.swing.JComponent;
import javax.swing.TransferHandler;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import dev.davamu.filebotpro.ui.filter.FileTree.FileNode;
import dev.davamu.filebotpro.ui.filter.FileTree.FolderNode;
import dev.davamu.filebotpro.ui.transfer.FileTransferable;
import dev.davamu.filebotpro.ui.transfer.TransferableExportHandler;

class FileTreeExportHandler implements TransferableExportHandler {

	@Override
	public Transferable createTransferable(JComponent c) {
		FileTree tree = (FileTree) c;
		LinkedHashSet<File> files = new LinkedHashSet<File>();

		for (TreePath path : tree.getSelectionPaths()) {
			TreeNode node = (TreeNode) path.getLastPathComponent();

			if (node instanceof FileNode) {
				files.add(((FileNode) node).getFile());
			} else if (node instanceof FolderNode) {
				for (Iterator<File> iterator = ((FolderNode) node).fileIterator(); iterator.hasNext();) {
					files.add(iterator.next());
				}
			}
		}

		if (files.isEmpty())
			return null;

		return new FileTransferable(files);
	}

	@Override
	public void exportDone(JComponent source, Transferable data, int action) {

	}

	@Override
	public int getSourceActions(JComponent c) {
		return TransferHandler.COPY;
	}

}
