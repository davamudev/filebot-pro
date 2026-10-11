package dev.davamu.filebotpro.ui;

import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

import dev.davamu.filebotpro.util.ui.MatteColorBorder;
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.Animator;
import com.formdev.flatlaf.util.CubicBezierEasing;

import net.miginfocom.swing.MigLayout;

/**
 * Page header with title, subtitle and optional actions on the right. Title changes fade in.
 */
public class HeaderPanel extends JComponent {

	private final JLabel titleLabel = new JLabel();
	private final JLabel subtitleLabel = new JLabel();
	private final JPanel actions = new JPanel(new MigLayout("insets 0, gap 6", "", "[center]"));

	private float textAlpha = 1;
	private Animator fadeAnimator;

	public HeaderPanel() {
		setLayout(new MigLayout("insets 14 24 14 16, fillx, hidemode 3", "[grow][]", "[]2[]"));
		setOpaque(true);

		titleLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "h2");
		subtitleLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "secondaryText");
		actions.setOpaque(false);

		add(titleLabel, "cell 0 0");
		add(subtitleLabel, "cell 0 1");
		add(actions, "cell 1 0 1 2, aligny center");

		updateUI();
	}

	@Override
	public void updateUI() {
		super.updateUI();
		setBackground(UIManager.getColor("App.header.background"));
		setBorder(new MatteColorBorder(0, 0, 1, 0, "Separator.foreground"));
		if (titleLabel != null) {
			titleLabel.setForeground(UIManager.getColor("App.header.foreground"));
		}
	}

	public void setTitle(String title) {
		titleLabel.setText(title);
		fadeIn();
	}

	public void setSubtitle(String subtitle) {
		subtitleLabel.setText(subtitle);
		subtitleLabel.setVisible(subtitle != null && !subtitle.isEmpty());
	}

	public JLabel getTitleLabel() {
		return titleLabel;
	}

	public void addAction(JComponent component) {
		actions.add(component);
	}

	private void fadeIn() {
		if (!Animator.useAnimation() || !isShowing()) {
			textAlpha = 1;
			repaint();
			return;
		}

		if (fadeAnimator == null) {
			fadeAnimator = new Animator(260, fraction -> {
				textAlpha = fraction;
				repaint();
			});
			fadeAnimator.setInterpolator(CubicBezierEasing.EASE_OUT);
			fadeAnimator.setResolution(10);
		}
		textAlpha = 0;
		fadeAnimator.restart();
	}

	@Override
	protected void paintComponent(Graphics g) {
		g.setColor(getBackground());
		g.fillRect(0, 0, getWidth(), getHeight());
	}

	@Override
	protected void paintChildren(Graphics g) {
		if (textAlpha >= 1) {
			super.paintChildren(g);
			return;
		}

		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, Math.min(1, textAlpha))));
			super.paintChildren(g2);
		} finally {
			g2.dispose();
		}
	}

}
