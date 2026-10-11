package dev.davamu.filebotpro.util.ui;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Clicking on the background of a window (panels, labels, empty space) takes the keyboard focus away from the focused
 * text field, like in a web browser. Swing keeps the focus in the text field otherwise, so it keeps showing the focus
 * border as if it was still selected.
 */
public class ClickAwayFocusHandler implements AWTEventListener {

	private static final ClickAwayFocusHandler INSTANCE = new ClickAwayFocusHandler();

	public static void install() {
		Toolkit.getDefaultToolkit().removeAWTEventListener(INSTANCE);
		Toolkit.getDefaultToolkit().addAWTEventListener(INSTANCE, AWTEvent.MOUSE_EVENT_MASK);
	}

	private final FocusAdapter resetFocusable = new FocusAdapter() {

		@Override
		public void focusLost(FocusEvent evt) {
			// the root pane only takes the focus temporarily, it must not become a stop of the Tab order
			evt.getComponent().setFocusable(false);
			evt.getComponent().removeFocusListener(this);
		}
	};

	@Override
	public void eventDispatched(AWTEvent event) {
		if (event.getID() != MouseEvent.MOUSE_PRESSED) {
			return;
		}

		Component c = ((MouseEvent) event).getComponent();
		Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

		if (!(focusOwner instanceof JTextComponent) || !isBackground(c) || SwingUtilities.getAncestorOfClass(JPopupMenu.class, c) != null) {
			return;
		}

		JRootPane rootPane = SwingUtilities.getRootPane(c);
		if (rootPane == null || rootPane != SwingUtilities.getRootPane(focusOwner)) {
			return;
		}

		if (!rootPane.isFocusable()) {
			rootPane.setFocusable(true);
			rootPane.addFocusListener(resetFocusable);
		}
		rootPane.requestFocusInWindow();
	}

	private boolean isBackground(Component c) {
		if (c instanceof JPanel || c instanceof JLabel || c instanceof JRootPane || c instanceof JLayeredPane || c instanceof JViewport || c instanceof JScrollPane || c instanceof JSplitPane || c instanceof JSeparator) {
			return true;
		}

		// plain containers without a UI delegate (e.g. the panels of the main window)
		return c instanceof JComponent && "ComponentUI".equals(((JComponent) c).getUIClassID());
	}

}
