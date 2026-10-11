
package dev.davamu.filebotpro.archive;


import java.io.File;
import java.io.IOException;
import java.io.OutputStream;


public interface ExtractOutProvider {

	OutputStream getStream(File archivePath) throws IOException;

}
