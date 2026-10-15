package dev.davamu.filebotpro.ui;

import static dev.davamu.filebotpro.ui.ThemeSupport.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import com.formdev.flatlaf.ui.FlatUIUtils;

import dev.davamu.filebotpro.Language;
import dev.davamu.filebotpro.ResourceManager;

public class LanguageComboBoxCellRenderer implements ListCellRenderer {

	private final ListCellRenderer base;
	private final Border padding;

	public LanguageComboBoxCellRenderer(ListCellRenderer base) {
		this.base = base;
		this.padding = new CompoundBorder(new EmptyBorder(2, 2, 2, 2), ((JLabel) base).getBorder());
	}

	@Override
	public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
		JLabel c = (JLabel) base.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

		Language language = (Language) value;
		c.setText(language.getName());
		c.setIcon(ResourceManager.getFlagIcon(language.getCode()));

		// heading above the first item of the recently used languages and of the full language list
		String heading = getSectionTitle((LanguageComboBoxModel) list.getModel(), index);
		c.setBorder(heading == null ? padding : new CompoundBorder(new SectionHeading(heading, list.getFont(), list.getBackground()), padding));

		return c;
	}

	private String getSectionTitle(LanguageComboBoxModel model, int index) {
		// index 0 is the default item (e.g. All Languages) and -1 the selected item in the combo box itself
		int favorites = model.favorites().size();

		if (index == 1 && favorites > 0) {
			return "Recently used";
		}

		// not "All languages", which is the default item of the subtitle panel
		if (index == 1 + favorites) {
			return "Languages";
		}

		return null;
	}

	private static class SectionHeading implements Border {

		private static final int GAP = 6;

		private final String text;
		private final Font font;
		private final Color background;

		public SectionHeading(String text, Font font, Color background) {
			this.text = text;
			this.font = font.deriveFont(Font.BOLD, font.getSize2D() - 1);
			this.background = background;
		}

		@Override
		public Insets getBorderInsets(Component c) {
			return new Insets(GAP + c.getFontMetrics(font).getHeight() + GAP / 2, 0, 0, 0);
		}

		@Override
		public boolean isBorderOpaque() {
			return true;
		}

		@Override
		public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
			Graphics2D g2d = (Graphics2D) g.create();
			int height = getBorderInsets(c).top;

			// not part of the selection
			g2d.setColor(background);
			g2d.fillRect(x, y, w, height);

			g2d.setColor(UIManager.getColor("Separator.foreground"));
			g2d.fillRect(x + GAP, y, w - 2 * GAP, 1);

			FontMetrics metrics = c.getFontMetrics(font);
			g2d.setFont(font);
			g2d.setColor(getSecondaryForeground());
			// aligned with the flag icon
			int left = ((JComponent) c).getInsets().left;
			FlatUIUtils.drawString((JComponent) c, g2d, text, x + left, y + GAP + metrics.getAscent());
			g2d.dispose();
		}
	}

}
