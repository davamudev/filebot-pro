package dev.davamu.filebotpro.format;

import static dev.davamu.filebotpro.similarity.Normalization.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;
import static dev.davamu.filebotpro.util.RegularExpressions.*;

import java.util.Arrays;
import java.util.regex.Pattern;

import javax.script.Bindings;
import javax.script.ScriptException;

public class ExpressionFileFormat extends ExpressionFormat {

	// a leading /, \\ or drive (C:\) that makes the destination absolute
	private static final Pattern ROOT = Pattern.compile("^\\s*(?:[/\\\\]+|[A-Za-z]:[/\\\\]+)+");

	// the leading code of a format that builds an absolute path: a quoted path, the environment or a binding that is a file or folder
	private static final Pattern ROOT_SOURCE = Pattern.compile("['\"]\\s*(?:[/\\\\]|[A-Za-z]:[/\\\\])|getenv|getProperty|\\b(?:file|f|folder|drive|home|output|relativeFile|mediaPath|history|files|plex|kodi|emby|jellyfin)\\b");

	private final boolean absoluteExpression;

	public ExpressionFileFormat(String expression) throws ScriptException {
		super(expression);
		this.absoluteExpression = isAbsoluteExpression(expression);
	}

	/**
	 * Only the start of the format can make the destination absolute: a literal path (/media/{n}) or a leading {...} that builds one
	 */
	protected static boolean isAbsoluteExpression(String expression) {
		String s = expression.stripLeading();
		if (ROOT.matcher(s).find()) {
			return true;
		}
		if (!s.startsWith("{")) {
			return false;
		}
		int level = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '{') {
				level++;
			} else if (c == '}' && --level == 0) {
				return ROOT_SOURCE.matcher(s.substring(1, i)).find();
			}
		}
		return ROOT_SOURCE.matcher(s).find();
	}

	@Override
	protected CharSequence normalizeExpressionValue(Object value) {
		// text that reaches the result through objects (e.g. {info.title}) is not sanitized by the bindings, so it must not be able to
		// turn the destination into an absolute path outside of the output folder, unless the format itself builds an absolute path
		if (value instanceof CharSequence && !absoluteExpression) {
			return ROOT.matcher(value.toString()).replaceFirst("");
		}
		return super.normalizeExpressionValue(value);
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
		return neutralizeParentSegments(normalizePathSeparators(replaceSpace(stripCRLF(value), " ").trim()));
	}

	/**
	 * Values that reach the result through objects (e.g. {info.title}) are not sanitized by the bindings, so ".." segments that the format itself does not contain must not navigate out of the destination folder
	 */
	protected String neutralizeParentSegments(String path) {
		int allowed = (int) Arrays.stream(getExpression().split("[/\\\\]")).filter(it -> it.trim().equals("..")).count();

		String[] segments = path.split("/", -1);
		for (int i = 0; i < segments.length; i++) {
			if (segments[i].trim().equals("..") && allowed-- <= 0) {
				segments[i] = "__";
			}
		}
		return String.join("/", segments);
	}

	protected String stripCRLF(CharSequence value) {
		return NEWLINE.matcher(value).replaceAll("");
	}

}
