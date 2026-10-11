package dev.davamu.filebotpro.ui;

import static dev.davamu.filebotpro.Settings.*;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JComponent;
import javax.swing.JFrame;

import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.util.ui.SwingEventBus;

public class SinglePanelFrame extends JFrame {

	public SinglePanelFrame(PanelBuilder builder) {
		super(String.format("%s %s", getApplicationName(), builder.getName()));

		JComponent panel = builder.create();

		HeaderPanel headerPanel = new HeaderPanel();
		headerPanel.setTitle(builder.getName());
		headerPanel.setSubtitle(builder.getDescription());
		headerPanel.getTitleLabel().setIcon(ResourceManager.getSvgIcon(builder.getSymbol(), 24, ThemeSupport::getAccentColor));
		headerPanel.getTitleLabel().setIconTextGap(12);
		headerPanel.addAction(AppMenus.createAppearanceButton());

		JComponent c = (JComponent) getContentPane();
		c.setLayout(new BorderLayout());
		c.add(headerPanel, BorderLayout.NORTH);
		c.add(panel, BorderLayout.CENTER);

		setSize(880, 620);
		setMinimumSize(new Dimension(800, 400));

		SwingEventBus.getInstance().register(panel);
	}

}
