package dev.davamu.filebotpro.cli;

import static dev.davamu.filebotpro.util.RegularExpressions.*;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

import dev.davamu.filebotpro.cli.PostProcess.Feature;

/**
 * Consume feature names (e.g. --apply artwork nfo, --apply artwork,nfo or --apply chmod=664) and custom actions (Groovy
 * closures or .groovy files) and stop at the first argument that is not a feature name, so file arguments may follow
 * the option.
 */
public class ApplyOptionHandler extends OptionHandler<String> {

	public ApplyOptionHandler(CmdLineParser parser, OptionDef option, Setter<? super String> setter) {
		super(parser, option, setter);
	}

	@Override
	public int parseArguments(Parameters params) throws CmdLineException {
		int pos = 0;
		while (pos < params.size()) {
			String parameter = params.getParameter(pos);
			if (isCustomAction(parameter)) {
				setter.addValue(parameter);
			} else if (isFeatureList(parameter)) {
				for (String it : COMMA.split(parameter)) {
					setter.addValue(it.trim());
				}
			} else {
				break;
			}
			pos++;
		}

		if (pos == 0) {
			throw new CmdLineException(owner, String.format("%s expects one or more of %s", option.toString(), Feature.names()), null);
		}

		return pos;
	}

	/**
	 * @return true for Groovy closures (e.g. {f -> println f}) and .groovy script files
	 */
	public static boolean isCustomAction(String parameter) {
		String s = parameter.trim();
		return (s.startsWith("{") && s.endsWith("}")) || s.endsWith(".groovy");
	}

	private boolean isFeatureList(String parameter) {
		for (String it : COMMA.split(parameter)) {
			if (!Feature.isName(it.trim())) {
				return false;
			}
		}
		return true;
	}

	@Override
	public String getDefaultMetaVariable() {
		return Feature.names().toString();
	}

}
