package dev.davamu.filebotpro.ui.rename;

import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Element;

import dev.davamu.filebotpro.ResourceManager;
import net.miginfocom.swing.MigLayout;

/**
 * Edit all new names at once, one name per line (e.g. to fix the same typo in every name or to paste a list of names)
 */
class EditNamesDialog extends JDialog {

	private final int count;

	private final JTextArea text = new JTextArea();
	private final JTextField find = new JTextField(15);
	private final JTextField replace = new JTextField(15);
	private final JCheckBox regex = new JCheckBox("Regex");
	private final JCheckBox columnEdit = new JCheckBox("Column Edit");
	private final JLabel status = new JLabel();

	private List<String> names = null;

	public EditNamesDialog(Window owner, List<String> names) {
		super(owner, "Edit Names", ModalityType.DOCUMENT_MODAL);
		this.count = names.size();

		text.setText(String.join("\n", names));
		text.setCaretPosition(0);

		// edits within the common prefix of all names (e.g. the series name) apply to every name
		columnEdit.setSelected(count > 1 && getCommonPrefixLength() > 0);
		columnEdit.setEnabled(count > 1);
		columnEdit.setToolTipText("Changes within the common prefix of all names apply to every name");
		((AbstractDocument) text.getDocument()).setDocumentFilter(new ColumnEditFilter());
		text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, UIManager.getFont("TextArea.font").getSize()));
		text.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(DocumentEvent e) {
				updateStatus();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				updateStatus();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				updateStatus();
			}
		});

		status.putClientProperty("FlatLaf.styleClass", "small");

		JComponent c = (JComponent) getContentPane();
		c.setLayout(new MigLayout("insets dialog, nogrid, fill", "", "[pref!][fill][pref!]"));

		c.add(new JLabel("Find:"));
		c.add(find, "growx");
		c.add(new JLabel("Replace:"), "gap 8px");
		c.add(replace, "growx");
		c.add(regex, "gap 8px");
		c.add(new JButton(replaceAction), "gap 8px, wrap");

		c.add(new JScrollPane(text), "grow, wrap");

		c.add(columnEdit, "tag left");
		c.add(status, "tag left");
		c.add(new JButton(cancelAction), "tag cancel");
		c.add(new JButton(okAction), "tag ok");

		installAction(c, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), cancelAction);
		installAction(find, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), replaceAction);
		installAction(replace, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), replaceAction);

		updateStatus();

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setMinimumSize(new Dimension(640, 420));
		pack();

		SwingUtilities.invokeLater(text::requestFocusInWindow);
	}

	/**
	 * @return the edited names (one for each row), or null if the dialog was cancelled
	 */
	public List<String> getNames() {
		return names;
	}

	private List<String> getLines() {
		// keep trailing empty lines, so that the line count is always correct
		return Arrays.asList(text.getText().split("\\R", -1));
	}

	/**
	 * Length of the common prefix of all lines
	 */
	private int getCommonPrefixLength() {
		List<String> lines = getLines();
		String first = lines.get(0);
		int length = first.length();
		for (String line : lines) {
			length = Math.min(length, line.length());
			for (int i = 0; i < length; i++) {
				if (line.charAt(i) != first.charAt(i)) {
					length = i;
					break;
				}
			}
		}
		return length;
	}

	/**
	 * Apply single-line edits within (or at the end of) the common prefix to the same column of every line
	 */
	private class ColumnEditFilter extends DocumentFilter {

		@Override
		public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
			replace(fb, offset, 0, string, attr);
		}

		@Override
		public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
			replace(fb, offset, length, "", null);
		}

		@Override
		public void replace(FilterBypass fb, int offset, int length, String string, AttributeSet attrs) throws BadLocationException {
			String insert = string == null ? "" : string;
			Element root = fb.getDocument().getDefaultRootElement();
			int line = root.getElementIndex(offset);

			// normal edit if disabled, for line breaks and for edits across multiple lines
			if (!columnEdit.isSelected() || insert.contains("\n") || root.getElementIndex(offset + length) != line || root.getElementCount() != count) {
				fb.replace(offset, length, string, attrs);
				return;
			}

			int column = offset - root.getElement(line).getStartOffset();
			if (column + length > getCommonPrefixLength()) {
				fb.replace(offset, length, string, attrs);
				return;
			}

			// bottom up, so that the offsets of the lines above stay valid
			for (int i = root.getElementCount() - 1; i >= 0; i--) {
				fb.replace(root.getElement(i).getStartOffset() + column, length, insert, attrs);
			}
		}
	}

	private void updateStatus() {
		int lines = getLines().size();
		boolean valid = lines == count && getLines().stream().noneMatch(String::isBlank);

		okAction.setEnabled(valid);
		status.setText(lines == count ? valid ? String.format("%d names", count) : "Names must not be empty" : String.format("%d lines for %d names", lines, count));
	}

	private final Action replaceAction = newAction("Replace All", ResourceManager.getIcon("action.edit"), evt -> {
		if (find.getText().isEmpty()) {
			return;
		}

		try {
			Pattern pattern = regex.isSelected() ? Pattern.compile(find.getText()) : Pattern.compile(Pattern.quote(find.getText()));
			String replacement = regex.isSelected() ? replace.getText() : Matcher.quoteReplacement(replace.getText());

			// replace line by line, so that patterns cannot match across names
			List<String> lines = getLines();
			lines.replaceAll(line -> pattern.matcher(line).replaceAll(replacement));
			text.setText(String.join("\n", lines));
		} catch (PatternSyntaxException | IndexOutOfBoundsException e) {
			status.setText(e.getMessage().lines().findFirst().orElse("Invalid regular expression"));
		}
	});

	private final Action cancelAction = newAction("Cancel", ResourceManager.getIcon("dialog.cancel"), evt -> setVisible(false));

	private final Action okAction = newAction("Apply", ResourceManager.getIcon("dialog.continue"), evt -> {
		names = getLines();
		setVisible(false);
	});

}
