/*
 * Created on 16.03.2005
 */

package dev.davamu.filebotpro.util.ui.notification;

import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.RoundRectangle2D;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;

import net.miginfocom.swing.MigLayout;

/**
 * Toast message: rounded card with drop shadow (if the platform supports translucent windows). Click to copy the message to the clipboard and close it.
 */
public class MessageNotification extends NotificationWindow {

	private static final int SHADOW = 12;
	private static final int ARC = 16;
	private static final int TEXT_WIDTH = 300;

	public MessageNotification(String head, String text, Icon icon, int timeout) {
		this((Window) null, head, text, icon, timeout);
	}

	public MessageNotification(String head, String text, Icon icon) {
		this(head, text, icon, -1);
	}

	public MessageNotification(Window owner, String head, String text, Icon icon, int timeout) {
		super(owner, timeout);

		boolean translucent = isPerPixelTranslucencySupported(getGraphicsConfiguration());

		Card card = new Card(translucent);
		card.setLayout(new MigLayout("insets 14 14 14 18, gap 12 3", "[][grow, fill]", "[][]"));

		if (icon != null) {
			card.add(new JLabel(icon), "spany 2, aligny top");
		}

		JLabel headLabel = new JLabel(head);
		headLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "semibold");
		card.add(headLabel, "wrap");

		JLabel textLabel = new JLabel(String.format("<html><div style='width: %dpx'>%s</div></html>", UIScale.scale(TEXT_WIDTH), escapeHTML(text).replace("\n", "<br>")));
		textLabel.setForeground(UIManager.getColor("App.secondaryText"));
		card.add(textLabel);

		if (translucent) {
			setBackground(new Color(0, 0, 0, 0));

			JPanel shadow = new ShadowPane();
			shadow.setLayout(new BorderLayout());
			shadow.setBorder(BorderFactory.createEmptyBorder(SHADOW / 2, SHADOW, SHADOW + SHADOW / 2, SHADOW));
			shadow.add(card);
			setContentPane(shadow);
		} else {
			setContentPane(card);
		}

		pack();

		// copy message to clipboard
		getGlassPane().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		getGlassPane().addMouseListener(mouseClicked(evt -> copyToClipboard(text)));
	}

	private static class Card extends JPanel {

		private final boolean rounded;

		public Card(boolean rounded) {
			this.rounded = rounded;
			setOpaque(!rounded);
		}

		@Override
		protected void paintComponent(Graphics g) {
			Color background = UIManager.getColor("App.notification.background");
			Color border = UIManager.getColor("App.notification.border");

			if (!rounded) {
				g.setColor(background);
				g.fillRect(0, 0, getWidth(), getHeight());
				g.setColor(border);
				g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
				return;
			}

			Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				float arc = UIScale.scale((float) ARC);
				RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, arc, arc);
				g2.setColor(background);
				g2.fill(shape);
				g2.setColor(border);
				g2.draw(shape);
			} finally {
				g2.dispose();
			}
		}
	}

	private static class ShadowPane extends JPanel {

		public ShadowPane() {
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics g) {
			if (getComponentCount() == 0) {
				return;
			}

			JComponent card = (JComponent) getComponent(0);
			Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(Color.black);

				float arc = UIScale.scale((float) ARC);
				int layers = UIScale.scale(SHADOW);
				for (int i = layers; i > 0; i--) {
					float t = 1 - (float) i / layers;
					g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.018f + 0.035f * t * t));
					g2.fill(new RoundRectangle2D.Float(card.getX() - i, card.getY() - i + layers / 3f, card.getWidth() + 2 * i, card.getHeight() + 2 * i, arc + 2 * i, arc + 2 * i));
				}
			} finally {
				g2.dispose();
			}
		}
	}

}
