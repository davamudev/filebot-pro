
package dev.davamu.filebotpro.web;


import java.nio.ByteBuffer;

import dev.davamu.filebotpro.vfs.FileInfo;


public interface SubtitleDescriptor extends FileInfo {

	@Override
	String getName();


	String getLanguageName();


	@Override
	String getType();


	ByteBuffer fetch() throws Exception;

}
