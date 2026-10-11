package dev.davamu.filebotpro.cli;

import java.util.Collection;

/**
 * Command-line interface that can ask the user (--mode interactive)
 */
public interface InteractiveCmdline {

	<T> T showInputDialog(Collection<T> options, String title, String message) throws Exception;

}
