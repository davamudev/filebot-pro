package dev.davamu.filebotpro.ui.transfer;

import static dev.davamu.filebotpro.Logging.*;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.InvalidDnDOperationException;
import java.util.logging.Level;

import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.TransferHandler.TransferSupport;

public abstract class TransferablePolicy {

	public abstract boolean accept(Transferable tr) throws Exception;

	public abstract void handleTransferable(Transferable tr, TransferAction action) throws Exception;

	public boolean canImport(TransferSupport support) {
		if (support.isDrop()) {
			support.setShowDropLocation(false);
		}

		try {
			return accept(support.getTransferable());
		} catch (InvalidDnDOperationException e) {
			// final drop may cause this exception because, the transfer data can only be accessed
			// *after* the drop has been accepted, but canImport is called before that

			// just assume that the transferable will be accepted, accept will be called in importData again anyway
			return true;
		} catch (Exception e) {
			debug.log(Level.WARNING, e, e::getMessage);
			return false;
		}
	}

	public boolean importData(TransferSupport support) {
		Transferable transferable = support.getTransferable();

		try {
			if (accept(transferable)) {
				TransferAction action = getTransferAction(support);

				if (support.isDrop()) {
					// read the dropped data now, but complete the drop before doing any real work, so the drag source (e.g.
					// Windows Explorer) is not blocked while folders are listed or files are parsed
					Transferable data = snapshot(transferable);
					SwingUtilities.invokeLater(() -> {
						try {
							handleTransferable(data, action);
						} catch (Exception e) {
							debug.log(Level.WARNING, e, e::getMessage);
						}
					});
				} else {
					handleTransferable(transferable, action);
				}
				return true;
			}
		} catch (Exception e) {
			debug.log(Level.WARNING, e, e::getMessage);
		}

		// transferable was not accepted, or transfer failed
		return false;
	}

	/**
	 * Copy of the dropped data that stays valid after the drop has been completed
	 */
	protected Transferable snapshot(Transferable tr) throws Exception {
		if (FileTransferable.hasFileListFlavor(tr)) {
			return new FileTransferable(FileTransferable.getFilesFromTransferable(tr));
		}
		if (tr.isDataFlavorSupported(DataFlavor.stringFlavor)) {
			return new StringSelection(tr.getTransferData(DataFlavor.stringFlavor).toString());
		}
		return tr;
	}

	protected TransferAction getTransferAction(TransferSupport support) {
		if (support.isDrop()) {
			return TransferAction.fromDnDConstant(support.getDropAction());
		}

		// use ADD by default (e.g. clipboard transfers)
		return TransferAction.ADD;
	}

	public static enum TransferAction {
		PUT(TransferHandler.MOVE), ADD(TransferHandler.COPY), LINK(TransferHandler.LINK);

		private final int dndConstant;

		private TransferAction(int dndConstant) {
			this.dndConstant = dndConstant;
		}

		public static TransferAction fromDnDConstant(int dndConstant) {
			for (TransferAction action : values()) {
				if (dndConstant == action.dndConstant)
					return action;
			}

			throw new IllegalArgumentException("Unsupported dndConstant: " + dndConstant);
		}

	}

}
