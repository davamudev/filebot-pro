package dev.davamu.filebotpro.ui;

import static javax.swing.BorderFactory.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.ui.ThemeSupport.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.ComboBoxEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicButtonUI;
import com.formdev.flatlaf.ui.FlatComboBoxUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.util.ui.SelectButton;
import dev.davamu.filebotpro.util.ui.SwingUI;
import net.miginfocom.swing.MigLayout;

public class SelectButtonTextField<T> extends JComponent {

	private SelectButton<T> selectButton = new SelectButton<T>();

	private JComboBox<Object> editor = new JComboBox<Object>() {

		@Override
		public void updateUI() {
			// keep the custom UI and the typed text when the look and feel changes (e.g. switching between light and dark theme)
			String text = getEditorText(this);
			setUI(new TextFieldComboBoxUI(selectButton));
			if (text != null && !text.equals(getEditorText(this))) {
				((JTextComponent) getEditor().getEditorComponent()).setText(text);
			}
		}
	};

	public SelectButtonTextField() {
		selectButton.addActionListener(textFieldFocusOnClick);

		setLayout(new MigLayout("fill, nogrid, novisualpadding"));
		add(selectButton, "w pref!, sizegroupy editor");
		add(editor, "gap 4, w 220px!, sizegroupy editor");

		editor.setPrototypeDisplayValue("X");
		editor.setRenderer(new CompletionCellRenderer());
		editor.setMaximumRowCount(10);

		SwingUI.installAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.CTRL_DOWN_MASK), new SpinClientAction(-1));
		SwingUI.installAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.CTRL_DOWN_MASK), new SpinClientAction(1));
	}

	public String getText() {
		JTextComponent component = getEditorComponent(editor);
		return component == null ? "" : component.getText();
	}

	/**
	 * Text field of the combo box editor, independent of the installed UI delegate (which is replaced while the look and feel changes).
	 */
	private static JTextComponent getEditorComponent(JComboBox<?> comboBox) {
		ComboBoxEditor comboBoxEditor = comboBox.getEditor();
		return comboBoxEditor != null && comboBoxEditor.getEditorComponent() instanceof JTextComponent ? (JTextComponent) comboBoxEditor.getEditorComponent() : null;
	}

	private static String getEditorText(JComboBox<?> comboBox) {
		JTextComponent component = getEditorComponent(comboBox);
		return component == null ? null : component.getText();
	}

	public JComboBox getEditor() {
		return editor;
	}

	public SelectButton<T> getSelectButton() {
		return selectButton;
	}

	private final ActionListener textFieldFocusOnClick = new ActionListener() {

		@Override
		public void actionPerformed(ActionEvent e) {
			getEditor().requestFocus();
		}

	};

	private class SpinClientAction extends AbstractAction {

		private int spin;

		public SpinClientAction(int spin) {
			super(String.format("Spin%+d", spin));
			this.spin = spin;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			selectButton.spinValue(spin);
		}
	}

	private class CompletionCellRenderer extends DefaultListCellRenderer {

		@Override
		public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

			JTextComponent component = getEditorComponent(editor);
			String highlightText = component == null ? "" : component.getText().substring(0, component.getSelectionStart());

			// nothing to highlight, plain text is much faster than HTML
			if (highlightText.isEmpty()) {
				setText(value.toString());
				return this;
			}

			// highlight the matching sequence
			Matcher matcher = Pattern.compile(highlightText, Pattern.LITERAL | Pattern.CASE_INSENSITIVE).matcher(value.toString());

			// use no-break, because we really don't want line-wrapping in our table cells
			StringBuffer htmlText = new StringBuffer("<html><nobr>");
			if (matcher.find()) {
				if (isSelected) {
					matcher.appendReplacement(htmlText, "<span style='font-weight: bold;'>$0</span>");
				} else {
					Color accent = UIManager.getColor("Component.accentColor");
					matcher.appendReplacement(htmlText, "<span style='color: " + SwingUI.toHex(accent != null ? accent : list.getSelectionBackground()) + "; font-weight: bold;'>$0</span>");
				}
			}
			matcher.appendTail(htmlText);
			htmlText.append("</nobr></html>");

			setText(htmlText.toString());
			return this;
		}
	}

	private static class TextFieldComboBoxUI extends FlatComboBoxUI {

		private SelectButton<?> button;

		public TextFieldComboBoxUI(SelectButton<?> button) {
			this.button = button;
		}

		@Override
		protected void configureEditor() {
			JTextComponent editor = getEditor();

			editor.setEnabled(comboBox.isEnabled());
			editor.setFocusable(comboBox.isFocusable());
			editor.setFont(comboBox.getFont());
			editor.setBorder(createEmptyBorder(0, 3, 0, 3));

			// let the combo box paint the rounded background, the square editor background would hide the rounded corners of the border
			editor.setOpaque(false);

			// listeners are removed again in unconfigureEditor(), the editor component outlives this UI when the look and feel changes
			editorFocusListener = createFocusListener();
			editor.addFocusListener(editorFocusListener);

			editorDocument = editor.getDocument();
			editorDocument.addDocumentListener(editorDocumentListener);

			// massive performance boost for list rendering is cell height is fixed
			popup.getList().setPrototypeCellValue("X");
		}

		private FocusListener editorFocusListener;
		private Document editorDocument;

		private final DocumentListener editorDocumentListener = new DocumentListener() {

			@Override
			public void changedUpdate(DocumentEvent e) {
				popup.getList().repaint();
			}

			@Override
			public void insertUpdate(DocumentEvent e) {
				popup.getList().repaint();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				popup.getList().repaint();
			}
		};

		@Override
		protected void unconfigureEditor() {
			super.unconfigureEditor();

			if (editorFocusListener != null) {
				editor.removeFocusListener(editorFocusListener);
				editorFocusListener = null;
			}
			if (editorDocument != null) {
				editorDocument.removeDocumentListener(editorDocumentListener);
				editorDocument = null;
			}
		}

		public JTextComponent getEditor() {
			return (JTextComponent) editor;
		}

		@Override
		protected ComboPopup createPopup() {
			// FlatLaf popup (rounded border, padding and full width selection like all other combo boxes)
			return new FlatComboPopup(comboBox) {

				@Override
				protected JList createList() {
					JList list = super.createList();

					// fixed cell size before the popup selects the current item, otherwise the list measures every (HTML) item, which takes seconds for long search histories
					list.setPrototypeCellValue("X");
					return list;
				}

				@Override
				public void show(Component invoker, int x, int y) {
					super.show(invoker, x - button.getWidth() - 4, y + 2);
				}

				@Override
				protected Rectangle computePopupBounds(int px, int py, int pw, int ph) {
					Rectangle bounds = super.computePopupBounds(px, py, pw, ph);
					bounds.width += button.getWidth() + 4;

					return bounds;
				}
			};
		}

		@Override
		protected FocusListener createFocusListener() {
			return new FocusHandler() {

				@Override
				public void focusGained(FocusEvent e) {
					if (comboBox == null) {
						return; // UI has been uninstalled
					}
					super.focusGained(e);
					comboBox.repaint(); // update the focus border of the whole combo box, not only the editor
				}

				/**
				 * Prevent action events from being fired on focusLost.
				 */
				@Override
				public void focusLost(FocusEvent e) {
					if (comboBox == null) {
						return; // UI has been uninstalled
					}
					if (isPopupVisible(comboBox)) {
						setPopupVisible(comboBox, false);
					}
					comboBox.repaint(); // update the focus border of the whole combo box, not only the editor
				}
			};
		}

	}

}
