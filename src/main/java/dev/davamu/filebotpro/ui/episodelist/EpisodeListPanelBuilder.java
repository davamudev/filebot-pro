
package dev.davamu.filebotpro.ui.episodelist;

import javax.swing.Icon;
import javax.swing.JComponent;

import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.ui.PanelBuilder;

public class EpisodeListPanelBuilder implements PanelBuilder {

	@Override
	public String getName() {
		return "Episodes";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("panel.episodelist");
	}

	@Override
	public String getDescription() {
		return "Browse and export episode lists from online databases.";
	}

	@Override
	public String getSymbol() {
		return "episodes";
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof EpisodeListPanelBuilder;
	}

	@Override
	public int hashCode() {
		return EpisodeListPanelBuilder.class.hashCode();
	}

	@Override
	public JComponent create() {
		return new EpisodeListPanel();
	}

}
