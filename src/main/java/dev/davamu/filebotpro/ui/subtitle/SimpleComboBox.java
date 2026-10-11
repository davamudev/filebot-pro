package dev.davamu.filebotpro.ui.subtitle;

import static javax.swing.BorderFactory.*;

import java.awt.Rectangle;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.plaf.basic.ComboPopup;

import com.formdev.flatlaf.ui.FlatComboBoxUI;

public class SimpleComboBox extends JComboBox {

	private final Icon dropDownArrowIcon;

	public SimpleComboBox(Icon dropDownArrowIcon) {
		this.dropDownArrowIcon = dropDownArrowIcon;
		updateUI();
	}

	@Override
	public void updateUI() {
		// keep the custom UI when the look and feel changes (e.g. switching between light and dark theme)
		if (dropDownArrowIcon != null) {
			setUI(new SimpleComboBoxUI(dropDownArrowIcon));
			setBorder(createEmptyBorder());
		} else {
			super.updateUI(); // called by the JComboBox constructor before the icon is set
		}
	}

	private static class SimpleComboBoxUI extends FlatComboBoxUI {

		private final Icon dropDownArrowIcon;

		public SimpleComboBoxUI(Icon dropDownArrowIcon) {
			this.dropDownArrowIcon = dropDownArrowIcon;
		}

		@Override
		protected JButton createArrowButton() {
			JButton button = new JButton(dropDownArrowIcon);
			button.setContentAreaFilled(false);
			button.setBorderPainted(false);
			button.setFocusPainted(false);
			button.setOpaque(false);

			return button;
		}

		@Override
		protected ComboPopup createPopup() {
			// FlatLaf popup (rounded border, padding and selection like all other combo boxes)
			return new FlatComboPopup(comboBox) {

				@Override
				protected Rectangle computePopupBounds(int px, int py, int pw, int ph) {
					Rectangle bounds = super.computePopupBounds(px, py, pw, ph);

					// allow combobox popup to be wider than the combobox itself
					bounds.width = Math.max(bounds.width, list.getPreferredSize().width);

					return bounds;
				}
			};
		}
	}

}
