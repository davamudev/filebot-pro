package dev.davamu.filebotpro.ui.theme;

import java.beans.PropertyChangeListener;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;

import com.formdev.flatlaf.ui.FlatComboBoxUI;

/**
 * {@link FlatComboBoxUI} where clicking a non-editable combo box doesn't move the keyboard focus to it, so it doesn't
 * keep the focus border (and look pressed) after the popup is closed. Keyboard navigation (Tab) still focuses it.
 * Installed via ComboBoxUI in FlatLaf.properties.
 */
public class AppComboBoxUI extends FlatComboBoxUI {

	public static ComponentUI createUI(JComponent c) {
		return new AppComboBoxUI();
	}

	private final PropertyChangeListener editableListener = evt -> updateRequestFocusEnabled();

	@Override
	public void installUI(JComponent c) {
		super.installUI(c);
		updateRequestFocusEnabled(); // after super.installUI(), the basic UI resets it while installing
	}

	@Override
	protected void installListeners() {
		super.installListeners();
		comboBox.addPropertyChangeListener("editable", editableListener);
	}

	@Override
	protected void uninstallListeners() {
		comboBox.removePropertyChangeListener("editable", editableListener);
		comboBox.setRequestFocusEnabled(true);
		super.uninstallListeners();
	}

	private void updateRequestFocusEnabled() {
		JComboBox<?> c = comboBox;
		c.setRequestFocusEnabled(c.isEditable());
	}

}
