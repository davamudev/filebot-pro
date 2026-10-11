package dev.davamu.filebotpro.vfs;

import java.io.File;

public interface FileInfo {

	public String getPath();

	public String getName();

	public String getType();

	public long getLength();

	public File toFile();

}
