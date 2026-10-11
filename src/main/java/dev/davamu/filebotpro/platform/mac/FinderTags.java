package dev.davamu.filebotpro.platform.mac;

import static dev.davamu.filebotpro.platform.mac.xattr.XAttrUtil.*;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import dev.davamu.filebotpro.util.BinaryPropertyList;

/**
 * Finder tags (the com.apple.metadata:_kMDItemUserTags extended attribute is a binary property list with an array of
 * "name" or "name\ncolor" strings)
 */
public final class FinderTags {

	public static final String XATTR_KEY = "com.apple.metadata:_kMDItemUserTags";

	public static List<String> getTags(File file) {
		byte[] value = getXAttrBytes(getPath(file), XATTR_KEY);
		if (value == null) {
			return new ArrayList<String>();
		}

		// remove color index
		List<String> tags = new ArrayList<String>();
		for (String tag : BinaryPropertyList.decodeStringArray(value)) {
			tags.add(tag.replaceFirst("\\n\\d+$", ""));
		}
		return tags;
	}

	/**
	 * Add the given tags and keep the existing ones
	 */
	public static void addTags(File file, Collection<String> tags) throws IOException {
		String path = getPath(file);
		byte[] value = getXAttrBytes(path, XATTR_KEY);

		List<String> entries = value == null ? new ArrayList<String>() : new ArrayList<String>(BinaryPropertyList.decodeStringArray(value));
		List<String> names = getTags(file);
		for (String tag : tags) {
			if (!names.contains(tag)) {
				entries.add(tag);
				names.add(tag);
			}
		}

		if (setXAttr(path, XATTR_KEY, BinaryPropertyList.encodeStringArray(entries)) != 0) {
			throw new IOException("Failed to set Finder tags: " + file);
		}
	}

	private static String getPath(File file) {
		// MacOS filesystem may require NFD unicode decomposition
		return Normalizer.normalize(file.getAbsolutePath(), Form.NFD);
	}

	private FinderTags() {
		throw new UnsupportedOperationException();
	}

}
