
package dev.davamu.filebotpro.ui.sfv;

import static dev.davamu.filebotpro.hash.VerificationUtilities.*;
import static dev.davamu.filebotpro.ui.ThemeSupport.*;

import java.awt.Graphics;
import java.awt.event.MouseEvent;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;

import dev.davamu.filebotpro.util.ui.EmptyState;
import dev.davamu.filebotpro.util.ui.SwingUI.DragDropRowTableUI;

class ChecksumTable extends JTable {

	public ChecksumTable() {
		setFillsViewportHeight(true);
		setAutoCreateRowSorter(true);
		setAutoCreateColumnsFromModel(true);
		setAutoResizeMode(AUTO_RESIZE_SUBSEQUENT_COLUMNS);

		setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		setDragEnabled(true);
		setFillsViewportHeight(true);

		// highlight CRC32 patterns in filenames in green and with smaller font-size
		setDefaultRenderer(String.class, new HighlightPatternCellRenderer(EMBEDDED_CHECKSUM));
		setDefaultRenderer(ChecksumRow.State.class, new StateIconCellRenderer());
		setDefaultRenderer(ChecksumCell.class, new ChecksumCellRenderer());
	}

	private final EmptyState emptyState = new EmptyState("verify", "Drop files or checksum files here", "SFV, MD5 and SHA files are verified, all other files are hashed");

	@Override
	public void updateUI() {
		// keep row drag-and-drop behaviour when the look and feel changes
		setUI(new DragDropRowTableUI());
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		if (getRowCount() == 0) {
			emptyState.paint(this, g);
		}
	}

	@Override
	protected ChecksumTableModel createDefaultDataModel() {
		return new ChecksumTableModel();
	}

	@Override
	protected JTableHeader createDefaultTableHeader() {
		return new JTableHeader(columnModel) {

			@Override
			public String getToolTipText(MouseEvent evt) {
				try {
					int columnIndex = columnModel.getColumnIndexAtX(evt.getX());
					int modelIndex = columnModel.getColumn(columnIndex).getModelIndex();

					// display column root of checksum column
					return getModel().getColumnRoot(modelIndex).getPath();
				} catch (Exception e) {
					// ignore, column is not a checksum column
					return null;
				}
			};
		};
	}

	@Override
	public ChecksumTableModel getModel() {
		return (ChecksumTableModel) super.getModel();
	}

	@Override
	public void createDefaultColumnsFromModel() {
		super.createDefaultColumnsFromModel();

		for (int i = 0; i < getColumnCount(); i++) {
			TableColumn column = getColumnModel().getColumn(i);

			if (i == 0) {
				column.setPreferredWidth(45);
			} else if (i == 1) {
				column.setPreferredWidth(400);
			} else if (i >= 2) {
				column.setPreferredWidth(150);
			}
		}
	}

}
