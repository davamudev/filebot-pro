package dev.davamu.filebotpro.ui;

import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.border.TitledBorder;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.swing.DefaultEventListModel;
import dev.davamu.filebotpro.ui.transfer.DefaultTransferHandler;
import dev.davamu.filebotpro.ui.transfer.TextFileExportHandler;
import dev.davamu.filebotpro.ui.transfer.TransferablePolicy;
import dev.davamu.filebotpro.util.ui.DefaultFancyListCellRenderer;
import dev.davamu.filebotpro.util.ui.DropHighlight;
import dev.davamu.filebotpro.util.ui.EmptyState;
import dev.davamu.filebotpro.util.ui.SwingUI;

public class TitledList<E> extends JComponent {

	protected EventList<E> model = new BasicEventList<E>();

	protected JList<E> list = new JList<E>(new DefaultEventListModel<E>(model)) {

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);

			if (emptyState != null && getModel().getSize() == 0) {
				emptyState.paint(this, g);
			}

			if (dragOver) {
				DropHighlight.paint(this, g);
			}
		}
	};

	private boolean dragOver = false;


	private EmptyState emptyState;

	protected JScrollPane listScrollPane = new JScrollPane(list);

	public TitledList() {
		setLayout(new BorderLayout());
		setBorder(new TitledBorder(getTitle()));

		list.setCellRenderer(new DefaultFancyListCellRenderer());
		list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		list.setTransferHandler(new DefaultTransferHandler(null, null));
		list.setDragEnabled(false);

		// highlight the list while files are dragged over it
		DropHighlight.install(list, active -> {
			dragOver = active;
			list.repaint();
		});

		add(listScrollPane, BorderLayout.CENTER);

		// Shortcut DELETE, disabled by default
		getRemoveAction().setEnabled(false);

		SwingUI.installAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), removeHook);
		SwingUI.installAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, KeyEvent.ALT_DOWN_MASK), removeHook);
	}

	public EventList<E> getModel() {
		return model;
	}

	public void setModel(EventList<E> model) {
		this.model = model;
		list.setModel(new DefaultEventListModel(model));
	}

	public JList<E> getListComponent() {
		return list;
	}

	public JScrollPane getListScrollPane() {
		return listScrollPane;
	}

	@Override
	public DefaultTransferHandler getTransferHandler() {
		return (DefaultTransferHandler) list.getTransferHandler();
	}

	public void setTransferablePolicy(TransferablePolicy transferablePolicy) {
		getTransferHandler().setTransferablePolicy(transferablePolicy);
	}

	public TransferablePolicy getTransferablePolicy() {
		return getTransferHandler().getTransferablePolicy();
	}

	public void setExportHandler(TextFileExportHandler exportHandler) {
		getTransferHandler().setExportHandler(exportHandler);

		// enable drag if export handler is available
		list.setDragEnabled(exportHandler != null);
	}

	public TextFileExportHandler getExportHandler() {
		return (TextFileExportHandler) getTransferHandler().getExportHandler();
	}

	/**
	 * Placeholder shown while the list is empty.
	 */
	public void setEmptyState(EmptyState emptyState) {
		this.emptyState = emptyState;
		list.repaint();
	}

	public String getTitle() {
		return (String) getClientProperty("title");
	}

	public void setTitle(String title) {
		putClientProperty("title", title);

		if (getBorder() instanceof TitledBorder) {
			TitledBorder border = (TitledBorder) getBorder();
			border.setTitle(title);

			repaint();
		}
	}

	private final AbstractAction defaultRemoveAction = new AbstractAction("Remove") {

		@Override
		public void actionPerformed(ActionEvent e) {
			int index = list.getSelectedIndex();

			for (Object value : list.getSelectedValuesList()) {
				getModel().remove(value);
			}

			int maxIndex = list.getModel().getSize() - 1;

			if (index > maxIndex) {
				index = maxIndex;
			}

			list.setSelectedIndex(index);
		}
	};

	private Action removeAction = defaultRemoveAction;

	public Action getRemoveAction() {
		return removeAction;
	}

	public void setRemoveAction(Action action) {
		this.removeAction = action;
	}

	private final AbstractAction removeHook = new AbstractAction("Remove") {

		@Override
		public void actionPerformed(ActionEvent e) {
			if (getRemoveAction() != null && getRemoveAction().isEnabled()) {
				getRemoveAction().actionPerformed(e);
			}
		}
	};

}
