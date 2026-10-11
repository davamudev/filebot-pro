package dev.davamu.filebotpro.ui;

import static java.awt.Cursor.*;
import static javax.swing.SwingUtilities.*;
import static dev.davamu.filebotpro.ui.ThemeSupport.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.prefs.Preferences;

import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicCheckBoxUI;

import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.util.ui.DefaultFancyListCellRenderer;
import dev.davamu.filebotpro.util.ui.RoundDecoration;
import dev.davamu.filebotpro.web.Movie;
import dev.davamu.filebotpro.web.SearchResult;
import dev.davamu.filebotpro.web.TheTVDBSearchResult;
import net.miginfocom.swing.MigLayout;

public class SelectDialog<T> extends JDialog {

	private JLabel messageLabel = new JLabel();
	private JToggleButton autoRepeatCheckBox;

	private JList<T> list;
	private String command;

	private Function<T, Icon> icon;

	// search filter
	private final List<T> allOptions;
	private final DefaultListModel<T> listModel = new DefaultListModel<T>();
	private JTextField searchField;

	public SelectDialog(Component parent, Collection<? extends T> options) {
		this(parent, options, null, false, false, null);
	}

	public SelectDialog(Component parent, Collection<? extends T> options, Function<T, Icon> icon, boolean autoRepeatEnabled, boolean autoRepeatSelected, JComponent header) {
		super(getWindow(parent), "Select", ModalityType.DOCUMENT_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		// enable icons
		this.icon = icon;

		// initialize list
		allOptions = new ArrayList<T>(options);
		listModel.addAll(allOptions);
		list = new JList<T>(listModel);

		// select first element
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setSelectedIndex(0);

		DefaultFancyListCellRenderer renderer = new DefaultFancyListCellRenderer(4) {

			@Override
			public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(list, convertValueToString(value), index, isSelected, cellHasFocus);
				configureValue(this, value);
				return this;
			}
		};

		renderer.setHighlightingEnabled(false);

		list.setCellRenderer(renderer);
		list.addMouseListener(mouseListener);

		// typing on the list moves focus to the search field
		list.addKeyListener(new KeyAdapter() {

			@Override
			public void keyTyped(KeyEvent evt) {
				char ch = evt.getKeyChar();
				if (!Character.isISOControl(ch) && !evt.isControlDown() && !evt.isMetaDown()) {
					evt.consume();
					searchField.requestFocusInWindow();
					searchField.setCaretPosition(searchField.getDocument().getLength());
					searchField.replaceSelection(String.valueOf(ch));
				}
			}
		});

		// search field
		searchField = new JTextField();
		searchField.putClientProperty("JTextField.placeholderText", "Search");
		searchField.putClientProperty("JTextField.showClearButton", true);
		searchField.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(DocumentEvent evt) {
				applyFilter();
			}

			@Override
			public void removeUpdate(DocumentEvent evt) {
				applyFilter();
			}

			@Override
			public void changedUpdate(DocumentEvent evt) {
				applyFilter();
			}
		});

		// keyboard navigation from search field
		installAction(searchField, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), newAction("Previous Item", evt -> moveSelection(-1)));
		installAction(searchField, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), newAction("Next Item", evt -> moveSelection(1)));
		installAction(searchField, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), newAction("Previous Page", evt -> moveSelection(-pageSize())));
		installAction(searchField, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), newAction("Next Page", evt -> moveSelection(pageSize())));

		JComponent c = (JComponent) getContentPane();
		c.setLayout(new MigLayout("insets 1.5mm 1.5mm 2.7mm 1.5mm, nogrid, novisualpadding, fill", "", header == null ? "[pref!][pref!][fill][pref!]" : "[min!][min!][pref!][fill][pref!]"));

		if (header != null) {
			c.add(header, "wmin 150px, hmin pref, growx, wrap");
		}
		c.add(messageLabel, "wmin 150px, hmin pref, growx, wrap");
		c.add(searchField, "wmin 150px, hmin pref, growx, wrap 1.5mm");
		c.add(new JScrollPane(list), "wmin 150px, hmin 150px, grow, wrap 2mm");

		c.add(accent(new JButton(selectAction)), "align center, id select");
		c.add(new JButton(cancelAction), "gap unrel, id cancel");

		// add repeat button
		if (autoRepeatEnabled) {
			autoRepeatCheckBox = createAutoRepeatCheckBox();
			c.add(autoRepeatCheckBox, "pos 1al select.y-3 n select.y2");
		}

		// set default size and location
		setMinimumSize(new Dimension(400, 330));

		// focus search field on open
		addWindowListener(new WindowAdapter() {

			@Override
			public void windowOpened(WindowEvent evt) {
				invokeLater(searchField::requestFocusInWindow);
			}
		});

		// Shortcut Enter
		installAction(list, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), selectAction);
		installAction(searchField, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), selectAction);

		applyFilter();
	}

	private void applyFilter() {
		List<String> words = new ArrayList<String>();
		for (String word : normalize(searchField.getText()).trim().split("\\s+")) {
			if (!word.isEmpty()) {
				words.add(word);
			}
		}

		// keep original order
		List<T> matches = new ArrayList<T>();
		for (T value : allOptions) {
			if (matchesAll(value, words)) {
				matches.add(value);
			}
		}

		listModel.clear();
		listModel.addAll(matches);

		// select first visible item
		if (listModel.isEmpty()) {
			list.clearSelection();
		} else {
			list.setSelectedIndex(0);
			list.ensureIndexIsVisible(0);
		}
		selectAction.setEnabled(!listModel.isEmpty());
	}

	private boolean matchesAll(T value, List<String> words) {
		List<String> texts = new ArrayList<String>();
		texts.add(normalize(convertValueToString(value)));

		if (value instanceof SearchResult) {
			for (String name : ((SearchResult) value).getEffectiveNames()) {
				texts.add(normalize(name));
			}
		}

		for (String word : words) {
			if (texts.stream().noneMatch(text -> text.contains(word))) {
				return false;
			}
		}
		return true;
	}

	private void moveSelection(int delta) {
		int size = listModel.getSize();
		if (size == 0) {
			return;
		}

		int index = list.getSelectedIndex();
		int next = index < 0 ? 0 : Math.max(0, Math.min(size - 1, index + delta));
		list.setSelectedIndex(next);
		list.ensureIndexIsVisible(next);
	}

	private int pageSize() {
		return Math.max(1, list.getLastVisibleIndex() - list.getFirstVisibleIndex());
	}

	private static String normalize(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase(Locale.ROOT);
	}

	protected JToggleButton createAutoRepeatCheckBox() {
		JCheckBox c = new JCheckBox();
		c.setUI(new BasicCheckBoxUI());
		c.setCursor(getPredefinedCursor(HAND_CURSOR));
		c.setIcon(createAutoRepeatCheckBoxIcon("button.repeat"));
		c.setSelectedIcon(createAutoRepeatCheckBoxIcon("button.repeat.selected"));
		c.addChangeListener(evt -> autoRepeatCheckBox.setToolTipText(autoRepeatCheckBox.isSelected() ? "Select and remember for next time" : "Select once and ask again next time"));
		return c;
	}

	protected Icon createAutoRepeatCheckBoxIcon(String name) {
		return new RoundDecoration(ResourceManager.getIcon(name), 28, 28, getPanelBackground(), getColor(0xD7D7D7));
	}

	protected String convertValueToString(Object value) {
		return value.toString();
	}

	protected void configureValue(DefaultFancyListCellRenderer render, Object value) {
		if (value instanceof SearchResult) {
			render.setToolTipText(getTooltipText((SearchResult) value));
		} else if (value instanceof File) {
			render.setToolTipText(((File) value).getAbsolutePath());
		} else {
			render.setToolTipText(null);
		}

		if (icon != null) {
			render.setIcon(icon.apply((T) value));
		}
	}

	protected String getTooltipText(SearchResult item) {
		StringBuilder html = new StringBuilder(64);
		html.append("<html><h3>").append(escapeHTML(item.toString())).append("</h3>");

		String[] names = item.getAliasNames();
		if (names.length > 0) {
			appendTooltipParagraph(html, "AKA", String.join(" | ", names));
		}

		if (item instanceof TheTVDBSearchResult) {
			TheTVDBSearchResult r = (TheTVDBSearchResult) item;

			appendTooltipParagraph(html, "First Aired", r.getFirstAired());
			appendTooltipParagraph(html, "Network", r.getNetwork());
			appendTooltipParagraph(html, "Status", r.getStatus());
			appendTooltipParagraph(html, "Overview", r.getOverview());
		}

		if (item instanceof Movie) {
			Movie m = (Movie) item;

			appendTooltipParagraph(html, "TheMovieDB", m.getTmdbId() > 0 ? m.getTmdbId() : null);
			appendTooltipParagraph(html, "IMDb", m.getImdbId() > 0 ? String.format("tt%07d", m.getImdbId()) : null);

			return html.append("</html>").toString();
		}

		appendTooltipParagraph(html, "ID", item.getId());

		return html.append("</html>").toString();
	}

	private StringBuilder appendTooltipParagraph(StringBuilder html, String label, Object value) {
		if (value != null) {
			html.append("<p style='width:250px; margin:3px'><b>").append(label).append(":</b> ").append(escapeHTML(value.toString())).append("</p>");
		}
		return html;
	}

	public JLabel getMessageLabel() {
		return messageLabel;
	}

	public JToggleButton getAutoRepeatCheckBox() {
		return autoRepeatCheckBox;
	}

	public String getSelectedAction() {
		return command;
	}

	public T getSelectedValue() {
		return SELECT.equals(command) ? list.getSelectedValue() : null;
	}

	public void close() {
		setVisible(false);
		dispose();
	}

	public Action getSelectAction() {
		return selectAction;
	}

	public Action getCancelAction() {
		return cancelAction;
	}

	public static final String SELECT = "Select";
	public static final String CANCEL = "Cancel";

	private final Action selectAction = newAction(SELECT, ResourceManager.getIcon("dialog.continue"), evt -> {
		command = SELECT;
		close();
	});

	private final Action cancelAction = newAction(CANCEL, ResourceManager.getIcon("dialog.cancel"), evt -> {
		command = CANCEL;
		close();
	});

	private final MouseAdapter mouseListener = mouseClicked(evt -> {
		if (isLeftMouseButton(evt) && (evt.getClickCount() == 2)) {
			selectAction.actionPerformed(new ActionEvent(evt.getSource(), ActionEvent.ACTION_PERFORMED, SELECT));
		}
	});

	private static final String KEY_REPEAT = "dialog.select.repeat";
	private static final String KEY_WIDTH = "dialog.select.width";
	private static final String KEY_HEIGHT = "dialog.select.height";

	public void saveState(Preferences prefs) {
		prefs.putInt(KEY_WIDTH, getWidth());
		prefs.putInt(KEY_HEIGHT, getHeight());

		if (autoRepeatCheckBox != null) {
			prefs.putBoolean(KEY_REPEAT, autoRepeatCheckBox.isSelected());
		}
	}

	public void restoreState(Preferences prefs) {
		setSize(prefs.getInt(KEY_WIDTH, getWidth()), prefs.getInt(KEY_HEIGHT, getHeight()));

		if (autoRepeatCheckBox != null) {
			autoRepeatCheckBox.setSelected(prefs.getBoolean(KEY_REPEAT, autoRepeatCheckBox.isSelected()));
		}
	}

}
