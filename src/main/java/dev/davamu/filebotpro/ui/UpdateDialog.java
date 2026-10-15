package dev.davamu.filebotpro.ui;

import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;

import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.UpdateCheck;
import net.miginfocom.swing.MigLayout;

/**
 * New release notice with the release notes of the newer versions. Resizable: the text wraps to the available width and the release notes take
 * whatever height is left, so the buttons stay visible on small screens and with large fonts.
 */
public class UpdateDialog extends JDialog {

	public UpdateDialog(Window owner, UpdateCheck.Update update) {
		super(owner, "Update available", ModalityType.APPLICATION_MODAL);

		// what's new since this version
		List<UpdateCheck.Release> changes = UpdateCheck.getChanges(update);

		JPanel pane = new JPanel(new MigLayout("fill, insets dialog", "[][grow, fill]", changes.isEmpty() ? "[]push[]" : "[][grow, fill][]"));
		setContentPane(pane);

		String message = String.format("<b>%s</b><br><br>%s<br>%s", escapeHTML(update.message()), escapeHTML(UpdateCheck.getInstructions()), escapeHTML("You have " + getApplicationIdentifier() + "."));
		pane.add(new JLabel(ResourceManager.getApplicationLogo(48)), "aligny top");
		pane.add(createText(message), "aligny top, gapleft 10, w 160:420, hmin pref, wrap paragraph");

		if (!changes.isEmpty()) {
			pane.add(new JScrollPane(createNotes(changes)), "skip, w 160:480, h 60:240, wrap paragraph");
		}

		pane.add(accent(newButton("Download", ResourceManager.getIcon("dialog.continue"), evt -> {
			openURI(update.download());
			setVisible(false);
		})), "span, split 3, tag ok");

		pane.add(newButton("Skip this version", ResourceManager.getIcon("dialog.cancel"), evt -> {
			UpdateCheck.ignore(update);
			setVisible(false);
		}), "tag help2");

		pane.add(newButton("Later", ResourceManager.getIcon("dialog.cancel"), evt -> {
			setVisible(false);
		}), "tag cancel");

		pane.registerKeyboardAction(evt -> setVisible(false), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);

		setResizable(true);

		// the first layout gives the wrapping text its width, the second one its height for that width
		pack();
		pack();

		// never smaller than the layout allows (measured at the minimum width, where the text needs the most lines), never larger than the screen
		Dimension size = getSize();
		layout(new Dimension(getMinimumWindowSize().width, size.height));
		setMinimumSize(getMinimumWindowSize());
		layout(size);

		setLocationRelativeTo(owner);
		setBounds(fitToScreen(getBounds(), owner));
	}

	private Dimension getMinimumWindowSize() {
		Dimension min = getRootPane().getMinimumSize();
		Insets frame = getInsets();
		return new Dimension(min.width + frame.left + frame.right, min.height + frame.top + frame.bottom);
	}

	private void layout(Dimension size) {
		setSize(size);
		for (int i = 0; i < 2; i++) {
			getContentPane().invalidate();
			validate();
		}
	}

	/**
	 * Read-only HTML text that wraps to the width it gets, and asks for a new layout when its height for the new width changes
	 */
	private static JEditorPane createText(String html) {
		JEditorPane text = new JEditorPane("text/html", "<html><body>" + html + "</body></html>");
		text.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
		text.setFont(UIManager.getFont("Label.font"));
		text.setForeground(UIManager.getColor("Label.foreground"));
		text.setBorder(BorderFactory.createEmptyBorder());
		text.setOpaque(false);
		text.setEditable(false);
		text.setFocusable(false);
		text.addComponentListener(new ComponentAdapter() {

			@Override
			public void componentResized(ComponentEvent evt) {
				if (text.getPreferredSize().height != text.getHeight()) {
					text.revalidate();
				}
			}
		});
		return text;
	}

	private static JEditorPane createNotes(List<UpdateCheck.Release> changes) {
		JEditorPane notes = new JEditorPane();
		notes.setContentType("text/html");
		notes.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);

		// own style rules in the document (the HTMLEditorKit style sheet is shared by the whole application)
		Font font = notes.getFont();
		Font mono = UIManager.getFont("monospaced.font");
		String css = String.join(" ", //
				"body { margin: 4px; }", //
				"p { margin-top: 0; margin-bottom: 4px; }", //
				"p.version { font-weight: bold; font-size: " + (font.getSize() + 2) + "pt; margin-top: 4px; }", //
				"p.section { font-weight: bold; margin-top: 6px; }", //
				"ul { margin-left-ltr: 18px; margin-right-rtl: 18px; margin-top: 0; margin-bottom: 4px; }", //
				"li { margin-bottom: 2px; }", //
				"code { font-family: '" + (mono != null ? mono.getFamily() : Font.MONOSPACED) + "'; font-size: " + font.getSize() + "pt; background-color: " + toHex(UIManager.getColor("App.code.background")) + "; color: " + toHex(UIManager.getColor("App.code.foreground")) + "; }");
		notes.setBackground(UIManager.getColor("App.surface"));
		notes.setText("<html><head><style>" + css + "</style></head><body>" + UpdateCheck.toHTML(changes) + "</body></html>");
		notes.setEditable(false);
		notes.addHyperlinkListener(evt -> {
			if (evt.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
				openURI(evt.getURL().toString());
			}
		});
		notes.setCaretPosition(0);
		return notes;
	}

}
