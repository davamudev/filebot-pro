package dev.davamu.filebotpro.ui;

import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import java.awt.Dialog.ModalityType;
import java.awt.Window;
import java.net.URI;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.ui.theme.ThemeManager;
import dev.davamu.filebotpro.util.ui.LinkButton;
import com.formdev.flatlaf.FlatClientProperties;

import net.miginfocom.swing.MigLayout;

/**
 * Version, license and the attribution of the online databases (TMDB requires its logo and notice in the application).
 */
public class AboutDialog extends JDialog {

	// notice for all data sources, which includes the attribution required by the TMDB terms of use (TMDB by that name)
	private static final String[] NOTICE = { "This product uses the APIs of TMDB and the other services above", "but is not endorsed or certified by any of them.", "Data and artwork belong to their respective services." };

	// name, logo (resources/sources/{logo}.svg or .png, small icon search.{logo}), website, and "label" for logos without the name
	private static final String[][] SOURCES = {
			{ "TheMovieDB", "themoviedb", "https://www.themoviedb.org" },
			{ "TheTVDB", "thetvdb", "https://thetvdb.com" },
			{ "TVmaze", "tvmaze", "https://www.tvmaze.com" },
			{ "AniDB", "anidb", "https://anidb.net" },
			{ "OMDb", "omdb", "https://www.omdbapi.com" },
			{ "OpenSubtitles", "opensubtitles", "https://www.opensubtitles.com", "label" },
			{ "AcoustID", "acoustid", "https://acoustid.org" },
			{ "Fanart.tv", "fanart", "https://fanart.tv" } };

	private static final int LOGO_WIDTH = 104;
	private static final int LOGO_HEIGHT = 42;
	private static final int LABELED_LOGO_SIZE = 28;

	public AboutDialog(Window owner) {
		super(owner, "About " + getApplicationName(), ModalityType.DOCUMENT_MODAL);

		JPanel content = new JPanel(new MigLayout("insets 28 32 24 32, wrap 1, fillx", "[center, grow, fill, 470lp]"));

		content.add(new JLabel(ResourceManager.getApplicationFullLogo(120, ThemeManager.isDark())), "growx 0, gapbottom 10");

		String commit = getApplicationCommit();
		JLabel version = new JLabel("Version " + getApplicationVersion() + " (r" + getApplicationRevisionNumber() + (commit == null ? "" : ", " + commit) + ")", SwingConstants.CENTER);
		version.putClientProperty(FlatClientProperties.STYLE_CLASS, "h4");
		content.add(version);

		JLabel license = new JLabel("<html><div style='text-align:center'>Rename and organize your movies, TV shows and anime.<br>Free software under the GNU General Public License v3.0.</div></html>", SwingConstants.CENTER);
		license.putClientProperty(FlatClientProperties.STYLE_CLASS, "secondaryText");
		content.add(license, "gapbottom 18");

		JLabel sources = new JLabel("Data sources");
		sources.putClientProperty(FlatClientProperties.STYLE_CLASS, "h4");
		content.add(sources, "growx 0, alignx left");

		JLabel intro = new JLabel(getApplicationName() + " uses data and artwork from these services:");
		intro.putClientProperty(FlatClientProperties.STYLE_CLASS, "secondaryText");
		content.add(intro, "gapbottom 6");

		content.add(createSourcesCard());

		JLabel terms = new JLabel("<html><div style='text-align:center'>" + String.join("<br>", NOTICE) + "</div></html>", SwingConstants.CENTER);
		terms.getAccessibleContext().setAccessibleName(String.join(" ", NOTICE));
		terms.putClientProperty(FlatClientProperties.STYLE_CLASS, "secondaryText");
		content.add(terms, "gaptop 8");

		JButton website = new JButton("Website");
		website.addActionListener(evt -> openURI(getApplicationProperty("link.website")));

		JButton close = new JButton("Close");
		close.putClientProperty(FlatClientProperties.STYLE_CLASS, "accent");
		close.addActionListener(evt -> dispose());

		JPanel buttons = new JPanel(new MigLayout("insets 0, fillx", "[]push[]"));
		buttons.setOpaque(false);
		buttons.add(website);
		buttons.add(close);
		content.add(buttons, "gaptop 16");

		content.registerKeyboardAction(evt -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);

		setContentPane(content);
		getRootPane().setDefaultButton(close);
		setResizable(false);
		pack();
		setLocationRelativeTo(owner);
	}

	private JComponent createSourcesCard() {
		JPanel card = new JPanel(new MigLayout("insets 8, wrap 4, fillx", "[grow, fill, sg]4[grow, fill, sg]4[grow, fill, sg]4[grow, fill, sg]", "[60lp]4[60lp]"));
		card.putClientProperty(FlatClientProperties.STYLE, "arc: 14; background: $App.surface; border: 1,1,1,1,$App.surfaceBorder,1,14");

		for (String[] source : SOURCES) {
			card.add(createSourceButton(source[0], source[1], URI.create(source[2]), source.length > 3), "grow");
		}

		return card;
	}

	/**
	 * Original logo of the source (with the name if the logo has none), or its small icon and name if there is no logo.
	 */
	private static JComponent createSourceButton(String name, String logo, URI website, boolean label) {
		Icon icon = label ? ResourceManager.getSourceLogo(logo, ThemeManager.isDark(), LABELED_LOGO_SIZE, LABELED_LOGO_SIZE) : ResourceManager.getSourceLogo(logo, ThemeManager.isDark(), LOGO_WIDTH, LOGO_HEIGHT);

		LinkButton button = new LinkButton(icon == null || label ? name : null, name + " – " + website.getHost(), icon == null ? ResourceManager.getIcon("search." + logo) : icon, website);
		button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
		button.setContentAreaFilled(true);
		button.setHorizontalAlignment(SwingConstants.CENTER);
		button.getAccessibleContext().setAccessibleName(name);

		return button;
	}

	public static void showAbout(Window owner) {
		new AboutDialog(owner).setVisible(true);
	}

}
