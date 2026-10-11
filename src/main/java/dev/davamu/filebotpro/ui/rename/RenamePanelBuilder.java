
package dev.davamu.filebotpro.ui.rename;

import javax.swing.Icon;
import javax.swing.JComponent;

import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.ui.PanelBuilder;

public class RenamePanelBuilder implements PanelBuilder {

	@Override
	public String getName() {
		return "Rename";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("panel.rename");
	}

	@Override
	public String getDescription() {
		return "Match your files with movie, episode and music data, then rename them all at once.";
	}

	@Override
	public String getSymbol() {
		return "rename";
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof RenamePanelBuilder;
	}

	@Override
	public JComponent create() {
		return new RenamePanel();
	}

}
