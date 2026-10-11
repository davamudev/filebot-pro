package dev.davamu.filebotpro.ui.filter;

import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.util.ExceptionUtilities.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;

import javax.swing.tree.TreeNode;

import dev.davamu.filebotpro.platform.mac.MacAppUtilities;
import dev.davamu.filebotpro.ui.filter.FileTree.FileNode;
import dev.davamu.filebotpro.ui.filter.FileTree.FolderNode;
import dev.davamu.filebotpro.ui.transfer.BackgroundFileTransferablePolicy;
import dev.davamu.filebotpro.util.FastFile;

class FileTreeTransferablePolicy extends BackgroundFileTransferablePolicy<TreeNode> {

	private final FileTree tree;

	public FileTreeTransferablePolicy(FileTree tree) {
		this.tree = tree;
	}

	@Override
	protected boolean accept(List<File> files) {
		return true;
	}

	@Override
	protected void clear() {
		super.clear();
		tree.clear();
	}

	@Override
	protected void process(List<TreeNode> root) {
		tree.getModel().setRoot(new FolderNode(root));
		tree.getModel().reload();
	}

	@Override
	protected void process(Exception e) {
		log.log(Level.WARNING, getRootCauseMessage(e), e);
	}

	@Override
	protected void load(List<File> files, TransferAction action) {

		try {
			// use fast file to minimize system calls like length(), isDirectory(), isFile(), ...
			TreeNode[] node = files.stream().map(FastFile::new).map(this::getTreeNode).toArray(TreeNode[]::new);

			// publish on EDT
			publish(node);
		} catch (CancellationException e) {
			// supposed to happen if background execution was aborted
		}
	}

	private TreeNode getTreeNode(File file) {
		if (Thread.interrupted()) {
			throw new CancellationException();
		}

		if (file.isDirectory()) {
			// folders first, then files, both in human name order
			List<TreeNode> folders = new ArrayList<TreeNode>();
			List<TreeNode> files = new ArrayList<TreeNode>();

			for (File f : getChildren(file, NOT_HIDDEN, HUMAN_NAME_ORDER)) {
				if (f.isDirectory()) {
					folders.add(getTreeNode(f));
				} else {
					files.add(getTreeNode(f));
				}
			}

			folders.addAll(files);
			return new FolderNode(file, getFolderName(file), folders);
		}

		return new FileNode(file);
	}

	@Override
	public String getFileFilterDescription() {
		return "Folders";
	}

	@Override
	public List<String> getFileFilterExtensions() {
		return null;
	}

}
