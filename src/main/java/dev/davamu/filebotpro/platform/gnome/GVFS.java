package dev.davamu.filebotpro.platform.gnome;

import java.io.File;

import dev.davamu.filebotpro.util.SystemProperty;

public interface GVFS {

	File getPathForURI(String resource);

	public static GVFS getDefaultVFS() {
		GVFS gvfs = SystemProperty.of("dev.davamu.filebotpro.gio.GVFS", path -> new PlatformGVFS(new File(path))).get();

		// default to native implementation GVFS folder is not set
		if (gvfs == null) {
			gvfs = new NativeGVFS();
		}

		return gvfs;
	}

}
