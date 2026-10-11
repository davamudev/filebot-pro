package dev.davamu.filebotpro.format;

import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;

import java.util.Collection;
import java.util.Objects;

public class SuppressedThrowables extends RuntimeException {

	public SuppressedThrowables(String message, Collection<Throwable> causes) {
		this(message, causes.toArray(new Throwable[0]));
	}

	public SuppressedThrowables(String message, Throwable... causes) {
		super(getMessage(message, causes), causes.length > 0 ? causes[causes.length - 1] : null); // last exception as default cause
	}

	private static String getMessage(String message, Throwable... causes) {
		if (causes.length == 0) {
			return message;
		}

		if (message == null || message.isEmpty()) {
			return getMessage(causes);
		}

		return message + ": " + getMessage(causes);
	}

	private static String getMessage(Throwable... causes) {
		return stream(causes).map(Throwable::getMessage).map(Objects::toString).distinct().collect(joining(" | "));
	}

}
