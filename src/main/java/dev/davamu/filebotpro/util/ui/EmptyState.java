package dev.davamu.filebotpro.util.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.UIManager;

import dev.davamu.filebotpro.ResourceManager;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;

/**
 * Friendly placeholder (icon, title and hint) painted in the middle of empty lists and tables.
 */
public class EmptyState {

	private final String symbol;
	private final String title;
	private final String hint;

	public EmptyState(String symbol, String title, String hint) {
		this.symbol = symbol;
		this.title = title;
		this.hint = hint;
	}

	public void paint(JComponent c, Graphics g) {
		Rectangle area = c.getVisibleRect();
		if (area.width < 120 || area.height < 90) {
			return;
		}

		Graphics2D g2 = (Graphics2D) g.create();
		try {
			FlatUIUtils.setRenderingHints(g2);

			Color muted = UIManager.getColor("Label.disabledForeground");
			Icon icon = symbol == null ? null : ResourceManager.getSvgIcon(symbol, 40, () -> muted);

			Font titleFont = UIManager.getFont("semibold.font");
			if (titleFont == null) {
				titleFont = c.getFont().deriveFont(Font.BOLD);
			}
			Font hintFont = c.getFont().deriveFont(c.getFont().getSize2D() - 1);

			FontMetrics titleMetrics = c.getFontMetrics(titleFont);
			FontMetrics hintMetrics = c.getFontMetrics(hintFont);

			int gap = UIScale.scale(10);
			int iconHeight = icon == null ? 0 : icon.getIconHeight() + gap;
			int height = iconHeight + titleMetrics.getHeight() + (hint == null ? 0 : UIScale.scale(4) + hintMetrics.getHeight());

			int y = area.y + (area.height - height) / 2;
			int cx = area.x + area.width / 2;

			if (icon != null) {
				icon.paintIcon(c, g2, cx - icon.getIconWidth() / 2, y);
				y += iconHeight;
			}

			g2.setFont(titleFont);
			g2.setColor(UIManager.getColor("App.secondaryText"));
			FlatUIUtils.drawString(c, g2, title, cx - titleMetrics.stringWidth(title) / 2, y + titleMetrics.getAscent());
			y += titleMetrics.getHeight() + UIScale.scale(4);

			if (hint != null) {
				g2.setFont(hintFont);
				g2.setColor(muted);
				FlatUIUtils.drawString(c, g2, hint, cx - hintMetrics.stringWidth(hint) / 2, y + hintMetrics.getAscent());
			}
		} finally {
			g2.dispose();
		}
	}

}
