package dev.davamu.filebotpro.ui.rename;

import static java.util.Collections.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;

import ca.odell.glazedlists.EventList;
import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.ui.TitledList;
import dev.davamu.filebotpro.ui.transfer.LoadAction;
import dev.davamu.filebotpro.util.ui.ActionPopup;
import dev.davamu.filebotpro.util.ui.PrototypeCellValueUpdater;
import net.miginfocom.swing.MigLayout;

class RenameList<E> extends TitledList<E> {

	private JPanel buttonPanel;

	public RenameList(EventList<E> model) {
		// replace default model with given model
		setModel(model);

		// multi-selection for removing or aligning multiple items at once
		list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		// need a fixed cell size for high performance scrolling
		list.setFixedCellHeight(28);
		list.getModel().addListDataListener(new PrototypeCellValueUpdater(list, ""));

		list.addMouseListener(dndReorderMouseAdapter);
		list.addMouseMotionListener(dndReorderMouseAdapter);

		getRemoveAction().setEnabled(true);

		buttonPanel = new JPanel(new MigLayout("insets 8px, nogrid, novisualpadding, fill", "align center"));
		buttonPanel.add(createImageButton(downAction), "sgy button");
		buttonPanel.add(createImageButton(upAction), "gap 4px, sgy button");
		buttonPanel.add(createLoadButton(), "gap 12px, sgy button");

		add(buttonPanel, BorderLayout.SOUTH);

		listScrollPane.getViewport().setBackground(list.getBackground());
	}

	public JPanel getButtonPanel() {
		return buttonPanel;
	}

	private JButton createLoadButton() {
		ActionPopup actionPopup = new ActionPopup("Load Files", ResourceManager.getIcon("action.load"));

		actionPopup.add(newAction("Select Folders", ResourceManager.getIcon("tree.closed"), evt -> {
			loadAction.actionPerformed(new ActionEvent(evt.getSource(), evt.getID(), evt.getActionCommand(), 0));
		}));

		actionPopup.add(newAction("Select Files", ResourceManager.getIcon("file.generic"), evt -> {
			loadAction.actionPerformed(new ActionEvent(evt.getSource(), evt.getID(), evt.getActionCommand(), ActionEvent.SHIFT_MASK));
		}));

		JButton button = new JButton(loadAction);
		button.setComponentPopupMenu(actionPopup);
		return button;
	}

	private final LoadAction loadAction = new LoadAction(this::getTransferablePolicy);

	private final Action upAction = newAction("Align Up", ResourceManager.getIcon("action.up"), evt -> {
		int[] indices = getListComponent().getSelectedIndices();

		// move each selected item up by one (top to bottom so that adjacent selected items move as a block)
		if (indices.length > 0 && indices[0] > 0) {
			for (int i = 0; i < indices.length; i++) {
				swap(model, indices[i], indices[i] - 1);
				indices[i]--;
			}
			getListComponent().setSelectedIndices(indices);
			getListComponent().ensureIndexIsVisible(indices[0]);
		}
	});

	private final Action downAction = newAction("Align Down", ResourceManager.getIcon("action.down"), evt -> {
		int[] indices = getListComponent().getSelectedIndices();

		// move each selected item down by one (bottom to top so that adjacent selected items move as a block)
		if (indices.length > 0 && indices[indices.length - 1] < model.size() - 1) {
			for (int i = indices.length - 1; i >= 0; i--) {
				swap(model, indices[i], indices[i] + 1);
				indices[i]++;
			}
			getListComponent().setSelectedIndices(indices);
			getListComponent().ensureIndexIsVisible(indices[indices.length - 1]);
		}
	});

	private final MouseAdapter dndReorderMouseAdapter = new MouseAdapter() {

		private int lastIndex = -1;

		@Override
		public void mousePressed(MouseEvent m) {
			// drag a single item (SHIFT / CTRL click extends the selection instead)
			boolean modifiers = m.isShiftDown() || m.isControlDown() || m.isMetaDown();
			lastIndex = modifiers ? -1 : getListComponent().locationToIndex(m.getPoint());
		}

		@Override
		public void mouseDragged(MouseEvent m) {
			int currentIndex = getListComponent().locationToIndex(m.getPoint());

			if (currentIndex != lastIndex && lastIndex >= 0 && currentIndex >= 0) {
				// move the dragged item step by step so that the items in between keep their order
				while (lastIndex < currentIndex) {
					swap(model, lastIndex, ++lastIndex);
				}
				while (lastIndex > currentIndex) {
					swap(model, lastIndex, --lastIndex);
				}

				// keep the dragged item selected (rather than the range selection of the drag gesture)
				getListComponent().setSelectedIndex(currentIndex);
			}
		}
	};

}
