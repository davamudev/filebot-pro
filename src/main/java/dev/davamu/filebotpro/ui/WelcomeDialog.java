package dev.davamu.filebotpro.ui;

import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.Dialog.ModalityType;
import java.awt.Window;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.ui.theme.ThemeManager;
import com.formdev.flatlaf.FlatClientProperties;

import net.miginfocom.swing.MigLayout;

/**
 * First-start dialog that points new users to the getting started guide.
 */
public class WelcomeDialog extends JDialog {

	private static final String[][] STEPS = {
			{ "rename", "Drop your files", "Drag movies, episodes or whole folders into the Rename panel." },
			{ "episodes", "Match", "Pick a database and FileBot Pro finds the matching movie or episode." },
			{ "verify", "Rename", "Preview the new names, tweak the format and rename everything at once." } };

	public WelcomeDialog(Window owner) {
		super(owner, "Welcome", ModalityType.DOCUMENT_MODAL);

		JPanel content = new JPanel(new MigLayout("insets 28 32 24 32, wrap 1, fillx", "[center, grow, fill, 400lp]"));

		JLabel logo = new JLabel(ResourceManager.getApplicationFullLogo(150, ThemeManager.isDark()));
		content.add(logo, "growx 0, gapbottom 10");

		JLabel title = new JLabel("Welcome to " + getApplicationName(), SwingConstants.CENTER);
		title.putClientProperty(FlatClientProperties.STYLE_CLASS, "h1");
		content.add(title);

		JLabel subtitle = new JLabel("<html><div style='text-align:center'>Organize and rename your movies, TV shows and anime,<br>and fetch subtitles and artwork in a few clicks.</div></html>", SwingConstants.CENTER);
		subtitle.putClientProperty(FlatClientProperties.STYLE_CLASS, "secondaryText");
		content.add(subtitle, "gapbottom 18");

		for (String[] step : STEPS) {
			content.add(createStep(step[0], step[1], step[2]), "gapbottom 8");
		}

		JButton later = new JButton("Maybe later");
		later.addActionListener(evt -> dispose());

		JButton guide = new JButton("Open the getting started guide");
		guide.putClientProperty(FlatClientProperties.STYLE_CLASS, "accent");
		guide.addActionListener(evt -> {
			openURI(getApplicationProperty("link.intro"));
			dispose();
		});

		JPanel buttons = new JPanel(new MigLayout("insets 0, fillx", "[]push[]"));
		buttons.setOpaque(false);
		buttons.add(later);
		buttons.add(guide);
		content.add(buttons, "gaptop 16");

		content.registerKeyboardAction(evt -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);

		setContentPane(content);
		getRootPane().setDefaultButton(guide);
		addWindowListener(new java.awt.event.WindowAdapter() {

			@Override
			public void windowOpened(java.awt.event.WindowEvent e) {
				guide.requestFocusInWindow();
			}
		});
		setResizable(false);
		pack();
		setLocationRelativeTo(owner);
	}

	private JComponent createStep(String symbol, String title, String description) {
		JPanel step = new JPanel(new MigLayout("insets 10 14 10 14, fillx", "[]14[grow, fill]", "[]2[]"));
		step.putClientProperty(FlatClientProperties.STYLE, "arc: 14; background: $App.surface; border: 1,1,1,1,$App.surfaceBorder,1,14");

		JLabel icon = new JLabel(ResourceManager.getSvgIcon(symbol, 22, ThemeSupport::getAccentColor));
		step.add(icon, "spany 2, aligny center");

		JLabel titleLabel = new JLabel(title);
		titleLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "h4");
		step.add(titleLabel, "wrap");

		JLabel descriptionLabel = new JLabel(description);
		descriptionLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "secondaryText");
		step.add(descriptionLabel);

		return step;
	}

	public static void showWelcome(Window owner) {
		new WelcomeDialog(owner).setVisible(true);
	}

}
