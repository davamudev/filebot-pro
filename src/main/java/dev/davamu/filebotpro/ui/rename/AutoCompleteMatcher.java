package dev.davamu.filebotpro.ui.rename;

import java.awt.Component;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import dev.davamu.filebotpro.similarity.Match;
import dev.davamu.filebotpro.web.SortOrder;

interface AutoCompleteMatcher {

	List<Match<File, ?>> match(Collection<File> files, boolean strict, SortOrder order, Locale locale, boolean autodetection, Component parent) throws Exception;
}
