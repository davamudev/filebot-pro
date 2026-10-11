package dev.davamu.filebotpro.cli;

import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.File;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;
import org.kohsuke.args4j.spi.StringOptionHandler;

public class GroovyExpressionHandler extends StringOptionHandler {

	public GroovyExpressionHandler(CmdLineParser parser, OptionDef option, Setter<? super String> setter) {
		super(parser, option, setter);
	}

	@Override
	public int parseArguments(Parameters params) throws CmdLineException {
		setter.addValue(getStringValue(params.getParameter(0)));
		return 1;
	}

	private String getStringValue(String s) throws CmdLineException {
		// try as file path, e.g. format.groovy or @format.txt
		if (s.endsWith(".groovy")) {
			File f = new File(s);
			if (f.isFile()) {
				return readExpressionFile(f);
			}
		}

		if (s.length() > 1 && s.startsWith("@")) {
			File f = new File(s.substring(1));
			if (f.isFile()) {
				return readExpressionFile(f);
			}
		}

		// or default to literal value
		return s;
	}

	private String readExpressionFile(File f) throws CmdLineException {
		try {
			// ignore UTF-8 BOM and trailing line breaks
			return readTextFile(f).replace("\uFEFF", "").strip();
		} catch (Exception e) {
			throw new CmdLineException(owner, "Failed to read text file: " + f, e);
		}
	}

	@Override
	public String getDefaultMetaVariable() {
		return "{expression}";
	}

}
