package dev.davamu.filebotpro.format;

import static dev.davamu.filebotpro.similarity.Normalization.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;
import static dev.davamu.filebotpro.util.RegularExpressions.*;

import javax.script.Bindings;
import javax.script.ScriptException;

public class ExpressionFileFormat extends ExpressionFormat {

	public ExpressionFileFormat(String expression) throws ScriptException {
		super(expression);
	}

	@Override
	public Bindings getBindings(Object value) {
		return new ExpressionBindings(value) {

			@Override
			public Object get(Object key) {
				return normalizeBindingValue(super.get(key));
			}
		};
	}

	protected Object normalizeBindingValue(Object value) {
		// if the binding value is a String, then remove illegal characters (that would insert accidental directory separators)
		if (value instanceof CharSequence) {
			String text = replacePathSeparators(value.toString(), " ");
			// a binding that is only dots (".", "..") must not be able to navigate out of the destination folder
			return text.matches("\\s*\\.+\\s*") ? text.replace('.', '_') : text;
		}

		// if the binding value is an Object, just leave it
		return value;
	}

	@Override
	protected String normalizeResult(CharSequence value) {
		// normalize unicode space characters and remove newline characters
		return normalizePathSeparators(replaceSpace(stripCRLF(value), " ").trim());
	}

	protected String stripCRLF(CharSequence value) {
		return NEWLINE.matcher(value).replaceAll("");
	}

}
