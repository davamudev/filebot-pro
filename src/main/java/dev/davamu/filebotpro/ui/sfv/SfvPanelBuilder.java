
package dev.davamu.filebotpro.ui.sfv;

import javax.swing.Icon;
import javax.swing.JComponent;

import dev.davamu.filebotpro.ResourceManager;
import dev.davamu.filebotpro.ui.PanelBuilder;

public class SfvPanelBuilder implements PanelBuilder {

	@Override
	public String getName() {
		return "SFV";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("panel.sfv");
	}

	@Override
	public String getDescription() {
		return "Create and verify SFV, MD5 and SHA checksum files.";
	}

	@Override
	public String getSymbol() {
		return "verify";
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof SfvPanelBuilder;
	}

	@Override
	public int hashCode() {
		return SfvPanelBuilder.class.hashCode();
	}

	@Override
	public JComponent create() {
		return new SfvPanel();
	}

}
