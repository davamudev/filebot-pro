package dev.davamu.filebotpro.ui;

import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.util.ui.SwingUI.*;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuBar;

public class AppMenuBar {

	public static JMenuBar createHelp() {
		JMenu help = new JMenu("Help");

		help.add(createLink("Getting Started", getApplicationProperty("link.intro")));
		help.add(createLink("Format Expressions", getApplicationProperty("link.format")));
		help.add(createLink("FAQ", getApplicationProperty("link.faq")));
		help.addSeparator();
		help.add(createLink("Troubleshooting", getApplicationProperty("link.help")));

		JMenuBar menuBar = new JMenuBar();
		menuBar.add(help);

		return menuBar;
	}

	private static Action createLink(final String title, final String uri) {
		return newAction(title, null, evt -> openURI(uri));
	}

}
