package dev.davamu.filebotpro.ui.rename;

import static java.util.Collections.*;
import static javax.swing.BorderFactory.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.Window;
import java.io.File;
import java.util.List;
import java.util.logging.Level;

import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;

import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import dev.davamu.filebotpro.Language;
import dev.davamu.filebotpro.RenameAction;
import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.StandardRenameAction;
import dev.davamu.filebotpro.UserFiles;
import dev.davamu.filebotpro.WebServices;
import dev.davamu.filebotpro.cli.FileOrder;
import dev.davamu.filebotpro.format.ExpressionFileFormat;
import dev.davamu.filebotpro.format.ExpressionFilter;
import dev.davamu.filebotpro.format.MediaBindingBean;
import dev.davamu.filebotpro.platform.mac.MacAppUtilities;
import dev.davamu.filebotpro.ui.HeaderPanel;
import dev.davamu.filebotpro.ui.rename.FormatDialog.Mode;
import dev.davamu.filebotpro.util.FileUtilities.ExtensionFileFilter;
import dev.davamu.filebotpro.web.Datasource;
import dev.davamu.filebotpro.web.EpisodeListProvider;
import dev.davamu.filebotpro.web.MovieIdentificationService;
import dev.davamu.filebotpro.web.SortOrder;
import net.miginfocom.swing.MigLayout;

public class PresetEditor extends JDialog {

	enum Result {
		SET, DELETE, CANCEL;
	}

	private Result result = Result.CANCEL;

	private HeaderPanel presetNameHeader;
	private JTextField pathInput;
	private RSyntaxTextArea filterEditor;
	private RSyntaxTextArea formatEditor;

	private JComboBox<Datasource> providerCombo;
	private JComboBox<SortOrder> sortOrderCombo;
	private JComboBox<Language> languageCombo;
	private JComboBox<String> matchModeCombo;
	private JComboBox<RenameAction> actionCombo;
	private JComboBox<String> shortcutCombo;
	private JComboBox<String> fileOrderCombo;

	private static final String SHORTCUT_AUTOMATIC = "Automatic";
	private static final String SHORTCUT_NONE = "None";
	private static final String[] SHORTCUTS = { SHORTCUT_AUTOMATIC, "1", "2", "3", "4", "5", "6", "7", "8", "9", SHORTCUT_NONE };

	private static final String FILE_ORDER_DEFAULT = "Default";
	private static final String[] FILE_ORDERS = { FILE_ORDER_DEFAULT, FileOrder.NAME, FileOrder.PATH, FileOrder.DATE, FileOrder.SIZE, FileOrder.RESOLUTION, "{-bytes}" };
	private static final String FILE_ORDER_TOOLTIP = "<html>Order of the files before matching (also decides which file wins a name conflict)<br><hr noshade>• name, path<br>• date (newest first), size (largest first)<br>• resolution (best video quality first)<br>• {expression} that yields a sort key, e.g. {-bytes} or {vf.minus('p') as int}</html>";

	private JRadioButton selectRadio;
	private JRadioButton inheritRadio;
	private JPanel inputPanel;

	public PresetEditor(Window owner) {
		super(owner, "Preset Editor", ModalityType.APPLICATION_MODAL);
		JComponent c = (JComponent) getContentPane();

		presetNameHeader = new HeaderPanel();

		inheritRadio = new JRadioButton("<html><nobr>Use <b>Original Files</b> selection</nobr></html>");
		selectRadio = new JRadioButton("<html><nobr>Do <b>Select</b> files</nobr></html>");
		pathInput = new JTextField(40);

		filterEditor = new FormatExpressionTextArea(new RSyntaxDocument(RSyntaxDocument.SYNTAX_STYLE_GROOVY));
		formatEditor = new FormatExpressionTextArea();

		actionCombo = createRenameActionCombo();
		providerCombo = createDataProviderCombo();
		sortOrderCombo = new JComboBox<SortOrder>(SortOrder.values());
		matchModeCombo = new JComboBox<String>(Preset.getSupportedMatchModes());
		languageCombo = createLanguageCombo();
		shortcutCombo = new JComboBox<String>(SHORTCUTS);
		shortcutCombo.setToolTipText("Number key that applies this preset in the Rename panel");
		fileOrderCombo = new JComboBox<String>(FILE_ORDERS);
		fileOrderCombo.setEditable(true);
		fileOrderCombo.setToolTipText(FILE_ORDER_TOOLTIP);

		inputPanel = new JPanel(new MigLayout("insets 0, fill"));
		inputPanel.setOpaque(false);
		inputPanel.add(new JLabel("Input Folder:"), "gap indent");
		inputPanel.add(pathInput, "growx, gap rel");
		inputPanel.add(createImageButton(selectInputFolder), "gap 0px, wrap");
		inputPanel.add(new JLabel("Includes:"), "gap indent, skip 1, split 3");
		inputPanel.add(wrapEditor(filterEditor), "growx, gap rel");
		inputPanel.add(createImageButton(listFiles), "gap rel");

		JPanel inputGroup = createGroupPanel("Files");
		inputGroup.add(selectRadio);
		inputGroup.add(inheritRadio, "wrap");
		inputGroup.add(inputPanel, "wrap");
		inputGroup.add(new JLabel("File Order:"));
		inputGroup.add(fileOrderCombo, "growx, gap rel");

		JPanel formatGroup = createGroupPanel("Format");
		formatGroup.add(new JLabel("Expression:"));
		formatGroup.add(wrapEditor(formatEditor), "growx, gap rel");
		formatGroup.add(createImageButton(editFormatExpression), "gap 10px");

		JPanel searchGroup = createGroupPanel("Options");
		searchGroup.add(new JLabel("Datasource:"), "sg label");
		searchGroup.add(providerCombo, "sg combo");
		searchGroup.add(new JLabel("Episode Order:"), "sg label, gap indent");
		searchGroup.add(sortOrderCombo, "sg combo, wrap");
		searchGroup.add(new JLabel("Language:"), "sg label");
		searchGroup.add(languageCombo, "sg combo");
		searchGroup.add(new JLabel("Match Mode:"), "sg label, gap indent");
		searchGroup.add(matchModeCombo, "sg combo, wrap");
		searchGroup.add(new JLabel("Rename Action:"), "sg label");
		searchGroup.add(actionCombo, "sg combo");
		searchGroup.add(new JLabel("Shortcut:"), "sg label, gap indent");
		searchGroup.add(shortcutCombo, "sg combo, wrap");

		c.setLayout(new MigLayout("insets dialog, hidemode 3, nogrid, fill"));
		c.add(presetNameHeader, "wmin 150px, hmin 75px, growx, dock north");
		c.add(inputGroup, "growx, wrap");
		c.add(formatGroup, "growx, wrap");
		c.add(searchGroup, "growx, wrap push");
		c.add(accent(new JButton(ok)), "tag apply");
		c.add(new JButton(delete), "tag cancel");

		ButtonGroup inputButtonGroup = new ButtonGroup();
		inputButtonGroup.add(inheritRadio);
		inputButtonGroup.add(selectRadio);
		inheritRadio.setSelected(true);
		selectRadio.addItemListener((evt) -> updateComponentStates());
		providerCombo.addItemListener((evt) -> updateComponentStates());
		updateComponentStates();

		setSize(730, 600);

		// add helpful tooltips
		filterEditor.setToolTipText(FILE_FILTER_TOOLTIP);
	}

	public void updateComponentStates() {
		Datasource ds = (Datasource) providerCombo.getSelectedItem();
		boolean input = selectRadio.isSelected();

		inputPanel.setVisible(input);
		sortOrderCombo.setEnabled(ds instanceof EpisodeListProvider);
		languageCombo.setEnabled(ds instanceof EpisodeListProvider || ds instanceof MovieIdentificationService);
		matchModeCombo.setEnabled(ds instanceof EpisodeListProvider || ds instanceof MovieIdentificationService);
	}

	public void setPreset(Preset p) {
		presetNameHeader.getTitleLabel().setText(p.getName());
		pathInput.setText(p.getInputFolder() == null ? "" : p.getInputFolder().getPath());
		filterEditor.setText(p.getIncludeFilter() == null ? "" : p.getIncludeFilter().getExpressionFilter().getExpression());
		formatEditor.setText(p.getFormat() == null ? "" : p.getFormat().getExpression());
		providerCombo.setSelectedItem(p.getDatasource() == null ? WebServices.TheTVDB : p.getDatasource());
		sortOrderCombo.setSelectedItem(p.getSortOrder() == null ? SortOrder.Airdate : p.getSortOrder());
		matchModeCombo.setSelectedItem(p.getMatchMode() == null ? RenamePanel.MATCH_MODE_OPPORTUNISTIC : p.getMatchMode());
		actionCombo.setSelectedItem(p.getRenameAction() == null ? StandardRenameAction.MOVE : p.getRenameAction());
		shortcutCombo.setSelectedItem(p.getShortcut() > 0 ? String.valueOf(p.getShortcut()) : p.getShortcut() < 0 ? SHORTCUT_NONE : SHORTCUT_AUTOMATIC);
		fileOrderCombo.setSelectedItem(p.getFileOrderValue() == null ? FILE_ORDER_DEFAULT : p.getFileOrderValue());

		// ugly hack, since Language objects only do object equality
		if (p.getLanguage() != null && !p.getLanguage().getCode().equals(((Language) languageCombo.getSelectedItem()).getCode())) {
			for (int i = 0; i < languageCombo.getModel().getSize(); i++) {
				if (p.getLanguage().getCode().equals(languageCombo.getModel().getElementAt(i).getCode())) {
					languageCombo.setSelectedIndex(i);
					break;
				}
			}
		}

		selectRadio.setSelected(p.getInputFolder() != null);
		updateComponentStates();
	}

	public Preset getPreset() throws Exception {
		String name = presetNameHeader.getTitleLabel().getText();
		File path = inheritRadio.isSelected() ? null : new File(pathInput.getText());
		ExpressionFilter includes = inheritRadio.isSelected() ? null : new ExpressionFilter(filterEditor.getText());
		ExpressionFileFormat format = formatEditor.getText().trim().isEmpty() ? null : new ExpressionFileFormat(formatEditor.getText());
		Datasource database = ((Datasource) providerCombo.getSelectedItem());
		SortOrder sortOrder = sortOrderCombo.isEnabled() ? ((SortOrder) sortOrderCombo.getSelectedItem()) : null;
		String matchMode = matchModeCombo.isEnabled() ? (String) matchModeCombo.getSelectedItem() : null;
		Language language = languageCombo.isEnabled() ? ((Language) languageCombo.getSelectedItem()) : null;
		StandardRenameAction action = actionCombo.isEnabled() ? (StandardRenameAction) actionCombo.getSelectedItem() : null;

		String shortcut = (String) shortcutCombo.getSelectedItem();
		shortcut = SHORTCUT_AUTOMATIC.equals(shortcut) ? null : SHORTCUT_NONE.equals(shortcut) ? Preset.SHORTCUT_NONE : shortcut;

		Object order = fileOrderCombo.getEditor().getItem();
		String fileOrder = order == null || FILE_ORDER_DEFAULT.equals(order.toString().trim()) ? null : order.toString().trim();
		if (fileOrder != null && !fileOrder.isEmpty()) {
			FileOrder.forName(fileOrder); // validate
		}

		return new Preset(name, path, includes, format, database, sortOrder, matchMode, language, action).withShortcut(shortcut).withFileOrder(fileOrder);
	}

	private JPanel createGroupPanel(String title) {
		JPanel inputGroup = new JPanel(new MigLayout("insets dialog, hidemode 3, nogrid, fill"));
		inputGroup.setBorder(createTitledBorder(title));
		return inputGroup;
	}

	private RTextScrollPane wrapEditor(RSyntaxTextArea editor) {
		RTextScrollPane scroll = new RTextScrollPane(editor, false);
		scroll.setLineNumbersEnabled(false);
		scroll.setFoldIndicatorEnabled(false);
		scroll.setIconRowHeaderEnabled(false);
		scroll.setVerticalScrollBarPolicy(RTextScrollPane.VERTICAL_SCROLLBAR_NEVER);
		scroll.setHorizontalScrollBarPolicy(RTextScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBackground(editor.getBackground());
		scroll.setOpaque(true);
		scroll.setBorder(pathInput.getBorder());
		return scroll;
	}

	private JComboBox<Datasource> createDataProviderCombo() {
		JComboBox<Datasource> combo = new JComboBox<Datasource>(Preset.getSupportedServices());

		ListCellRenderer<? super Datasource> renderer = combo.getRenderer();
		combo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
			JLabel label = (JLabel) renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

			if (value instanceof Datasource) {
				Datasource provider = (Datasource) value;
				label.setText(provider.getIdentifier());
				label.setIcon(provider.getIcon());
				label.setToolTipText(String.format("%s Mode: %s", Mode.getMode(provider), provider.getName()));
			}

			return label;
		});

		return combo;
	}

	private JComboBox<Language> createLanguageCombo() {
		JComboBox<Language> combo = new JComboBox<Language>(Preset.getSupportedLanguages());

		ListCellRenderer<? super Language> renderer = combo.getRenderer();
		combo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
			JLabel label = (JLabel) renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

			if (value instanceof Language) {
				Language it = value;
				label.setText(it.getName());
				label.setIcon(ResourceManager.getFlagIcon(it.getCode()));
			}

			return label;
		});

		return combo;
	}

	private JComboBox<RenameAction> createRenameActionCombo() {
		JComboBox<RenameAction> combo = new JComboBox<RenameAction>(Preset.getSupportedActions());

		ListCellRenderer<? super RenameAction> renderer = combo.getRenderer();
		combo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
			JLabel label = (JLabel) renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

			if (value instanceof StandardRenameAction) {
				StandardRenameAction it = (StandardRenameAction) value;
				label.setText(it.getDisplayName());
				label.setIcon(ResourceManager.getIcon("rename.action." + it.toString().toLowerCase()));
			}

			return label;
		});

		return combo;
	}

	public Result getResult() {
		return result;
	}

	private final Action selectInputFolder = newAction("Select Input Folder", ResourceManager.getIcon("action.load"), evt -> {
		File f = UserFiles.showOpenDialogSelectFolder(null, "Select Input Folder", evt);
		if (f != null) {
			pathInput.setText(f.getAbsolutePath());
		}
	});

	private final Action editFormatExpression = newAction("Open Format Editor", ResourceManager.getIcon("action.format"), evt -> {
		Mode mode = Mode.getMode((Datasource) providerCombo.getSelectedItem());

		Object sample = mode.getDefaultSampleObject();
		File file = null;

		if (mode == Mode.File) {
			List<File> files = UserFiles.showLoadDialogSelectFiles(false, false, null, new ExtensionFileFilter(ExtensionFileFilter.WILDCARD), "Select Sample File", evt);
			if (files.isEmpty()) {
				return;
			}
			sample = file = files.get(0);
		}

		FormatDialog dialog = new FormatDialog(getWindow(evt.getSource()), mode, new MediaBindingBean(sample, file, singletonMap(file, sample)), false);
		dialog.setFormatCode(formatEditor.getText());
		setOffsetLocation(dialog);
		dialog.setVisible(true);

		if (dialog.submit()) {
			formatEditor.setText(dialog.getFormat().getExpression());
		}
	});

	private final Action listFiles = newAction("List Files", ResourceManager.getIcon("action.search"), evt -> {
		withWaitCursor(evt.getSource(), () -> {
			try {
				Preset preset = getPreset();
				if (preset.getInputFolder() == null) {
					return;
				}


				List<File> files = preset.selectFiles();

				// display selected files as popup with easy access to more binding info
				JPopupMenu popup = new JPopupMenu();
				if (files.size() > 0) {
					for (File f : files) {
						popup.add(newAction(f.getPath(), e -> {
							BindingDialog dialog = new BindingDialog(getWindow(evt.getSource()), "File Bindings", Mode.File.getFormat(), false);
							setOffsetLocation(dialog, getWindow(evt.getSource()));
							dialog.setSample(new MediaBindingBean(f, f));
							dialog.setVisible(true);
						}));
					}
				} else {
					popup.add("No files selected").setEnabled(false);
				}

				JComponent source = (JComponent) evt.getSource();
				popup.show(source, -3, source.getHeight() + 4);
			} catch (Exception e) {
				log.log(Level.WARNING, "Invalid preset settings: " + e.getMessage(), e);
			}
		});
	});

	private final Action ok = newAction("Save Preset", ResourceManager.getIcon("dialog.continue"), evt -> {
		try {
			Preset preset = getPreset();
			if (preset != null) {
				result = Result.SET;
				setVisible(false);
			}
		} catch (Exception e) {
			log.log(Level.WARNING, "Invalid preset settings: " + e.getMessage(), e);
		}
	});

	private final Action delete = newAction("Delete Preset", ResourceManager.getIcon("dialog.cancel"), evt -> {
		result = Result.DELETE;
		setVisible(false);
	});

	private static final String FILE_FILTER_TOOLTIP = "<html>File Selector Expression<br><hr noshade>e.g.<br>• fn =~ /alias/<br>• ext =~ /mp4/<br>• minutes &gt; 100<br>• age &lt; 7<br>• file.isEpisode()<br>• …<br></html>";

}
