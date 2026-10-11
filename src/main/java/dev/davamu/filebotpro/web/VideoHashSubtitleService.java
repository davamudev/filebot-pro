package dev.davamu.filebotpro.web;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface VideoHashSubtitleService extends Datasource {

	public Map<File, List<SubtitleDescriptor>> getSubtitleList(File[] videoFiles, Locale locale) throws Exception;

	public URI getLink();

}
