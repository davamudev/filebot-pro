package dev.davamu.filebotpro.ui.filter;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

import dev.davamu.filebotpro.ApplicationFolder;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;

/**
 * Named, ordered selection of MediaInfo columns (e.g. {@code Video.Width}, {@code Audio[1].Language}) for the MediaInfo
 * table. Views are stored as JSON in the user data folder and can be exported and imported as {@code .json} files.
 */
class MediaInfoView {

	public static final MediaInfoView DEFAULT = new MediaInfoView("Default", null);

	private static final File STORE = ApplicationFolder.AppData.resolve("mediainfo-views.json");

	private final String name;
	private final List<String> columns;

	public MediaInfoView(String name, List<String> columns) {
		this.name = name;
		this.columns = columns == null ? null : unmodifiableList(new ArrayList<String>(columns));
	}

	public String getName() {
		return name;
	}

	/**
	 * @return selected columns in display order, or {@code null} for the default view that shows all columns
	 */
	public List<String> getColumns() {
		return columns;
	}

	public boolean isDefault() {
		return columns == null;
	}

	public MediaInfoView rename(String name) {
		return new MediaInfoView(name, columns);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof MediaInfoView && Objects.equals(name, ((MediaInfoView) obj).name) && Objects.equals(columns, ((MediaInfoView) obj).columns);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, columns);
	}

	@Override
	public String toString() {
		return isDefault() ? "Default (all columns)" : name;
	}

	private Map<String, Object> toMap() {
		Map<String, Object> json = new LinkedHashMap<String, Object>();
		json.put("name", name);
		json.put("columns", columns);
		return json;
	}

	public String toJson() {
		return JsonOutput.prettyPrint(JsonOutput.toJson(toMap()));
	}

	public static String toJson(List<MediaInfoView> views) {
		List<Map<String, Object>> json = new ArrayList<Map<String, Object>>();
		for (MediaInfoView view : views) {
			json.add(view.toMap());
		}
		return JsonOutput.prettyPrint(JsonOutput.toJson(json));
	}

	/**
	 * Parse a single view object or an array of view objects.
	 */
	public static List<MediaInfoView> fromJson(String json) {
		Object node = new JsonSlurper().parseText(json);
		List<MediaInfoView> views = new ArrayList<MediaInfoView>();

		for (Object item : node instanceof List ? (List<?>) node : singletonList(node)) {
			if (item instanceof Map) {
				Object name = ((Map<?, ?>) item).get("name");
				Object columns = ((Map<?, ?>) item).get("columns");

				if (name != null && name.toString().trim().length() > 0 && columns instanceof List) {
					List<String> names = new ArrayList<String>();
					for (Object column : (List<?>) columns) {
						if (column != null && column.toString().trim().length() > 0) {
							names.add(column.toString().trim());
						}
					}
					views.add(new MediaInfoView(name.toString().trim(), names));
				}
			}
		}

		if (views.isEmpty()) {
			throw new IllegalArgumentException("No MediaInfo views found");
		}
		return views;
	}

	public static List<MediaInfoView> load() {
		try {
			if (STORE.isFile()) {
				return fromJson(readTextFile(STORE));
			}
		} catch (Exception e) {
			debug.log(Level.WARNING, e, () -> "Failed to read MediaInfo views: " + STORE);
		}
		return new ArrayList<MediaInfoView>();
	}

	public static void store(List<MediaInfoView> views) throws IOException {
		if (views.isEmpty()) {
			STORE.delete();
		} else {
			STORE.getParentFile().mkdirs();
			writeFile(toJson(views).getBytes(UTF_8), STORE);
		}
	}

}
