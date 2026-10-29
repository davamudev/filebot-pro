package dev.davamu.filebotpro.ui.filter;

import static java.util.stream.Collectors.*;
import static javax.swing.KeyStroke.*;
import static dev.davamu.filebotpro.ui.ThemeSupport.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ToolTipManager;

import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.ui.filter.MediaInfoTool.MediaInfoData;
import dev.davamu.filebotpro.util.ui.LinkButton;
import dev.davamu.filebotpro.util.ui.LazyDocumentListener;
import net.miginfocom.swing.MigLayout;

/**
 * Dialog for choosing the name and the ordered columns of a {@link MediaInfoView}.
 */
class MediaInfoViewEditor extends JDialog {

	private static final String FIELDS_URL = "https://mediaarea.net/en/MediaInfo/Support/Fields";

	private final MediaInfoData data;
	private final List<String> available;
	private final Set<String> reservedNames;

	private final JTextField nameField = new JTextField(24);
	private final JTextField filterField = new JTextField();
	private final JTextField customField = new JTextField();
	private final JLabel statusLabel = new JLabel(" ");

	private final DefaultListModel<String> availableModel = new DefaultListModel<String>();
	private final DefaultListModel<String> selectedModel = new DefaultListModel<String>();

	private final JList<String> availableList = createColumnList(availableModel);
	private final JList<String> selectedList = createColumnList(selectedModel);

	private final JButton saveButton;

	private MediaInfoView result;

	/**
	 * @param data
	 *            properties of the files that are currently loaded
	 * @param reservedNames
	 *            names of other views that can't be used for this view
	 */
	public MediaInfoViewEditor(Window owner, String title, MediaInfoView view, MediaInfoData data, Set<String> reservedNames) {
		super(owner, title, ModalityType.APPLICATION_MODAL);

		this.data = data;
		this.available = data.getColumns();
		this.reservedNames = reservedNames;

		nameField.setText(view.getName());
		view.getColumns().forEach(selectedModel::addElement);

		filterField.putClientProperty("JTextField.placeholderText", "Filter");
		filterField.putClientProperty("JTextField.showClearButton", true);
		customField.putClientProperty("JTextField.placeholderText", "Property name, e.g. Video.Width");

		availableList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		selectedList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		availableList.setVisibleRowCount(16);
		selectedList.setVisibleRowCount(16);

		availableList.addMouseListener(mouseClicked(evt -> {
			if (evt.getClickCount() == 2) {
				addSelected();
			}
		}));
		selectedList.addMouseListener(mouseClicked(evt -> {
			if (evt.getClickCount() == 2) {
				removeSelected();
			}
		}));

		filterField.getDocument().addDocumentListener(new LazyDocumentListener(0, evt -> updateAvailable()));
		nameField.getDocument().addDocumentListener(new LazyDocumentListener(0, evt -> validateInput()));
		customField.addActionListener(evt -> addCustom());

		JButton addButton = createImageButton(newAction("Add Columns", ResourceManager.getIcon("column.add"), evt -> addSelected()));
		JButton removeButton = createImageButton(newAction("Remove Columns", ResourceManager.getIcon("column.remove"), evt -> removeSelected()));
		JButton upButton = createImageButton(newAction("Move Up", ResourceManager.getIcon("action.up"), evt -> move(-1)));
		JButton downButton = createImageButton(newAction("Move Down", ResourceManager.getIcon("action.down"), evt -> move(+1)));

		saveButton = accent(newButton("Save", ResourceManager.getIcon("dialog.continue"), evt -> save()));
		JButton cancelButton = newButton("Cancel", ResourceManager.getIcon("dialog.cancel"), evt -> setVisible(false));

		statusLabel.setForeground(getErrorColor());

		JComponent c = (JComponent) getContentPane();
		c.setLayout(new MigLayout("insets dialog, fill", "[grow, fill, sg list][pref!][grow, fill, sg list][pref!]", "[pref!]para[pref!][pref!][grow, fill][pref!]para[pref!]"));

		c.add(new JLabel("Name:"), "span, split 2");
		c.add(nameField, "growx, wrap");

		c.add(new JLabel(available.isEmpty() ? "Available columns (load files to list them)" : "Available columns"), "cell 0 1, split 2");
		c.add(new LinkButton("Field reference", "What each MediaInfo property means", null, URI.create(FIELDS_URL)), "cell 0 1, gapleft push, growx 0");
		c.add(new JLabel("Columns in view"), "cell 2 1");

		c.add(filterField, "cell 0 2");
		c.add(new JScrollPane(availableList), "cell 0 3, w 260:320, h 200:340");
		c.add(createButtonStack(addButton, removeButton), "cell 1 3");
		c.add(new JScrollPane(selectedList), "cell 2 2 1 2, w 260:320, h 200:340");
		c.add(createButtonStack(upButton, downButton), "cell 3 2 1 2");

		c.add(customField, "cell 0 4, split 2, growx");
		c.add(newButton("Add", evt -> addCustom()), "cell 0 4");

		c.add(statusLabel, "cell 0 5, span 2, growx");
		c.add(saveButton, "cell 2 5, span 2, split 2, tag ok");
		c.add(cancelButton, "cell 2 5, tag cancel");

		installAction(c, JComponent.WHEN_IN_FOCUSED_WINDOW, getKeyStroke(KeyEvent.VK_ESCAPE, 0), newAction("Cancel", evt -> setVisible(false)));
		getRootPane().setDefaultButton(saveButton);

		updateAvailable();
		validateInput();

		pack();
		setMinimumSize(getSize());
	}

	/**
	 * Column list with the description and an example value of each column as tooltip.
	 */
	private JList<String> createColumnList(DefaultListModel<String> model) {
		JList<String> list = new JList<String>(model) {

			@Override
			public String getToolTipText(MouseEvent evt) {
				int i = locationToIndex(evt.getPoint());
				return i >= 0 && getCellBounds(i, i).contains(evt.getPoint()) ? data.getToolTipText(getModel().getElementAt(i)) : null;
			}
		};
		ToolTipManager.sharedInstance().registerComponent(list);
		return list;
	}

	private static JPanel createButtonStack(JButton... buttons) {
		JPanel panel = new JPanel(new MigLayout("insets 0, flowy, aligny center, gap 0 6px"));
		panel.setOpaque(false);
		for (JButton button : buttons) {
			panel.add(button);
		}
		return panel;
	}

	/**
	 * @return the edited view, or {@code null} if the dialog was cancelled
	 */
	public MediaInfoView getView() {
		return result;
	}

	private List<String> getSelectedColumns() {
		List<String> columns = new ArrayList<String>();
		for (int i = 0; i < selectedModel.size(); i++) {
			columns.add(selectedModel.get(i));
		}
		return columns;
	}

	private void updateAvailable() {
		String filter = filterField.getText().trim().toLowerCase();
		List<String> selected = getSelectedColumns();

		availableModel.clear();
		availableModel.addAll(available.stream().filter(c -> !selected.contains(c)).filter(c -> c.toLowerCase().contains(filter)).collect(toList()));
	}

	private void addSelected() {
		List<String> columns = availableList.getSelectedValuesList();
		if (columns.isEmpty()) {
			return;
		}

		columns.forEach(selectedModel::addElement);
		updateAvailable();
		selectedList.setSelectionInterval(selectedModel.size() - columns.size(), selectedModel.size() - 1);
		validateInput();
	}

	private void addCustom() {
		String column = customField.getText().trim();
		if (column.isEmpty()) {
			return;
		}

		if (!selectedModel.contains(column)) {
			selectedModel.addElement(column);
			updateAvailable();
		}
		selectedList.setSelectedValue(column, true);
		customField.setText("");
		validateInput();
	}

	private void removeSelected() {
		int[] indices = selectedList.getSelectedIndices();
		for (int i = indices.length - 1; i >= 0; i--) {
			selectedModel.remove(indices[i]);
		}
		updateAvailable();
		validateInput();
	}

	private void move(int offset) {
		int[] indices = selectedList.getSelectedIndices();
		if (indices.length == 0 || indices[0] + offset < 0 || indices[indices.length - 1] + offset >= selectedModel.size()) {
			return;
		}

		// move the item next to the selection to the other side of the selection
		int from = offset < 0 ? indices[0] - 1 : indices[indices.length - 1] + 1;
		int to = offset < 0 ? indices[indices.length - 1] : indices[0];
		selectedModel.add(to, selectedModel.remove(from));

		for (int i = 0; i < indices.length; i++) {
			indices[i] += offset;
		}
		selectedList.setSelectedIndices(indices);
		selectedList.ensureIndexIsVisible(offset < 0 ? indices[0] : indices[indices.length - 1]);
	}

	private String validateInput() {
		String name = nameField.getText().trim();
		String error = null;

		if (name.isEmpty()) {
			error = "Enter a name for the view";
		} else if (reservedNames.contains(name)) {
			error = "A view with this name already exists";
		} else if (selectedModel.isEmpty()) {
			error = "Add at least one column";
		}

		statusLabel.setText(error == null ? " " : error);
		saveButton.setEnabled(error == null);
		return error;
	}

	private void save() {
		if (validateInput() == null) {
			result = new MediaInfoView(nameField.getText().trim(), getSelectedColumns());
			setVisible(false);
		}
	}

}
