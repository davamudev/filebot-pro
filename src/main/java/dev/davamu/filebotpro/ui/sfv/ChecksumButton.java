package dev.davamu.filebotpro.ui.sfv;

import java.awt.Cursor;

import javax.swing.Action;
import javax.swing.JToggleButton;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * Pill shaped toggle used to pick the hash function (SFV, MD5, SHA1, ...).
 */
public class ChecksumButton extends JToggleButton {

	public ChecksumButton(Action action) {
		super(action);

		putClientProperty(FlatClientProperties.STYLE, "arc: 999; margin: 3,12,3,12; font: -1");
		setFocusable(false);
		setEnabled(true);
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);

		// set appropriate cursor
		setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
	}

}
