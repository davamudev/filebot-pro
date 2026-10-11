package dev.davamu.filebotpro.cli;

import static java.util.Comparator.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import dev.davamu.filebotpro.format.ExpressionMapper;
import dev.davamu.filebotpro.format.MediaBindingBean;
import dev.davamu.filebotpro.media.VideoQuality;

/**
 * Order in which files are processed (e.g. --file-order resolution), which decides which file wins when multiple files are renamed to the
 * same destination (e.g. --conflict skip keeps the first one)
 */
public class FileOrder {

	public static final String NAME = "name";
	public static final String PATH = "path";
	public static final String DATE = "date";
	public static final String SIZE = "size";
	public static final String RESOLUTION = "resolution";

	public static final String[] VALUES = { NAME, PATH, DATE, SIZE, RESOLUTION };

	/**
	 * @param order
	 *            name, path, date (newest first), size (largest first), resolution (best video quality first) or a {expression}
	 *            that yields a sort key for each file (e.g. {-bytes} or {vf.minus('p') as int})
	 */
	public static Comparator<File> forName(String order) throws Exception {
		switch (order.trim().toLowerCase()) {
		case NAME:
			return HUMAN_NAME_ORDER;
		case PATH:
			return CASE_INSENSITIVE_PATH_ORDER;
		case DATE:
			return comparingLong(File::lastModified).reversed();
		case SIZE:
			return comparingLong(File::length).reversed();
		case RESOLUTION:
			return VideoQuality.DESCENDING_ORDER;
		default:
			if (order.startsWith("{") && order.endsWith("}")) {
				return forExpression(order);
			}
			throw new IllegalArgumentException(String.format("%s not in [%s] or {expression}", order, String.join(", ", VALUES)));
		}
	}

	private static Comparator<File> forExpression(String expression) throws Exception {
		ExpressionMapper mapper = new ExpressionMapper(expression);
		Map<File, Comparable<?>> keys = new HashMap<File, Comparable<?>>();

		Comparator<Comparable<?>> natural = (a, b) -> compare(a, b);
		return comparing((File f) -> keys.computeIfAbsent(f, k -> getSortKey(mapper, k)), nullsLast(natural));
	}

	private static Comparable<?> getSortKey(ExpressionMapper mapper, File file) {
		try {
			return mapper.map(new MediaBindingBean(file, file), Comparable.class);
		} catch (Exception e) {
			debug.warning(format("File order expression failed: %s [%s]", e, file));
			return null;
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static int compare(Comparable a, Comparable b) {
		try {
			return a.compareTo(b);
		} catch (ClassCastException e) {
			return a.toString().compareTo(b.toString()); // e.g. Integer and String
		}
	}

}
