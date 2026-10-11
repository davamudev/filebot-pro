
package dev.davamu.filebotpro.ui.rename;

import java.util.Map;

import dev.davamu.filebotpro.similarity.Match;

public interface MatchFormatter {

	public boolean canFormat(Match<?, ?> match);

	public String preview(Match<?, ?> match);

	public String format(Match<?, ?> match, boolean extension, Map<?, ?> context) throws Exception;

}
