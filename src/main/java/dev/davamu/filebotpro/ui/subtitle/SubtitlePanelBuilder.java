
package dev.davamu.filebotpro.ui.subtitle;

import javax.swing.Icon;
import javax.swing.JComponent;

import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.ui.PanelBuilder;

public class SubtitlePanelBuilder implements PanelBuilder {

	@Override
	public String getName() {
		return "Subtitles";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("panel.subtitle");
	}

	@Override
	public String getDescription() {
		return "Search and download subtitles for your videos.";
	}

	@Override
	public String getSymbol() {
		return "subtitles";
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof SubtitlePanelBuilder;
	}

	@Override
	public JComponent create() {
		return new SubtitlePanel();
	}

}
