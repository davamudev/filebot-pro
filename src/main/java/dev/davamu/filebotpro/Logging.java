package dev.davamu.filebotpro;

import static java.nio.channels.Channels.*;
import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.util.RegularExpressions.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.groovy.runtime.StackTraceUtils;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.Wincon;
import com.sun.jna.ptr.IntByReference;

import dev.davamu.filebotpro.util.SystemProperty;

public final class Logging {

	private static final SystemProperty<Level> debugLevel = SystemProperty.of("dev.davamu.filebotpro.logging.debug", Level::parse, Level.WARNING);
	private static final SystemProperty<Pattern> anonymizePattern = SystemProperty.of("dev.davamu.filebotpro.logging.anonymize", Pattern::compile);
	private static final SystemProperty<Boolean> color = SystemProperty.of("dev.davamu.filebotpro.logging.color", Boolean::parseBoolean, EscapeCode.isSupported());
	private static final SystemProperty<Boolean> timeStamp = SystemProperty.of("dev.davamu.filebotpro.logging.time", Boolean::parseBoolean, false);
	private static final SystemProperty<DateTimeFormatter> timeStampFormat = SystemProperty.of("dev.davamu.filebotpro.logging.time.format", DateTimeFormatter::ofPattern, DateTimeFormatter.ofPattern("'['uuuu-MM-dd HH:mm:ss.SSS']' "));

	public static final Logger log = createConsoleLogger("dev.davamu.filebotpro.console", Level.ALL);
	public static final Logger debug = createConsoleLogger("dev.davamu.filebotpro.debug", debugLevel.get());

	public static Logger createConsoleLogger(String name, Level level) {
		Logger log = Logger.getLogger(name);
		log.setUseParentHandlers(false);
		log.setLevel(level);
		log.addHandler(createConsoleHandler(level));
		return log;
	}

	public static StreamHandler createSimpleFileHandler(File file, Level level) throws IOException {
		StreamHandler handler = new StreamHandler(new FileOutputStream(file, true), new SimpleFormatter());
		handler.setEncoding("UTF-8");
		handler.setLevel(level);
		return handler;
	}

	public static StreamHandler createLogFileHandler(File file, boolean lock, Level level) throws IOException {
		if (!file.exists() && !file.getParentFile().mkdirs() && !file.createNewFile()) {
			throw new IOException("Failed to create log file: " + file);
		}

		// open file channel and lock
		FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
		if (lock) {
			try {
				log.config("Locking " + file);
				channel.lock();
			} catch (Exception e) {
				throw new IOException("Failed to acquire lock: " + file, e);
			}
		}

		StreamHandler handler = new StreamHandler(newOutputStream(channel), new ConsoleFormatter(anonymizePattern.get(), false, createTimeStampFormat()));
		handler.setEncoding("UTF-8");
		handler.setLevel(level);
		return handler;
	}

	public static ConsoleHandler createConsoleHandler(Level level) {
		ConsoleHandler handler = new ConsoleHandler();
		handler.setLevel(level);
		handler.setFormatter(new ConsoleFormatter(anonymizePattern.get(), color.get(), createTimeStampFormat()));
		return handler;
	}

	private static DateTimeFormatter createTimeStampFormat() {
		return timeStamp.get() ? timeStampFormat.get().withLocale(Locale.US).withZone(ZoneId.systemDefault()) : null;
	}

	public static Supplier<String> format(String format, Object... args) {
		return () -> String.format(format, args);
	}

	public static Supplier<String> formatSingleLine(String format, Object... args) {
		return () -> NEWLINE.splitAsStream(String.format(format, args)).map(String::trim).filter(s -> !s.isEmpty()).collect(joining(" ↲ "));
	}

	public static Supplier<String> trace(Throwable t) {
		return () -> {
			StringBuilder s = new StringBuilder();
			s.append(t.getClass().getSimpleName()).append(": ");
			s.append(t.getMessage());

			StackTraceElement[] trace = StackTraceUtils.sanitizeRootCause(t).getStackTrace();
			if (trace != null && trace.length > 0) {
				s.append(" at ").append(trace[0]);
			}
			return s.toString();
		};
	}

	public static Supplier<String> cause(Object m, Throwable t) {
		return () -> getMessage(m, t);
	}

	public static Supplier<String> cause(Throwable t) {
		return () -> getMessage(null, t);
	}

	public static Supplier<String> message(Object... elements) {
		return () -> getMessage(elements);
	}

	public static String getMessage(Object m, Throwable t) {
		// try to unravel stacked exceptions
		if (t.getCause() != null && (t instanceof RuntimeException || t instanceof ExecutionException || t instanceof InvocationTargetException)) {
			return getMessage(m, t.getCause());
		}

		// e.g. Failed to create file: AccessDeniedException: /path/to/file
		return getMessage(m, t.getClass().getSimpleName(), t.getMessage());
	}

	public static String getMessage(Object... elements) {
		return stream(elements).filter(Objects::nonNull).map(Objects::toString).collect(joining(": "));
	}

	public static class ConsoleFormatter extends Formatter {

		private final Pattern anonymize;
		private final boolean colorize;
		private final DateTimeFormatter dateTimeFormatter;

		public ConsoleFormatter(Pattern anonymize, boolean colorize, DateTimeFormatter dateTimeFormatter) {
			this.anonymize = anonymize;
			this.colorize = colorize;
			this.dateTimeFormatter = dateTimeFormatter;
		}

		@Override
		public String format(LogRecord record) {
			EscapeCode color = getColor(record.getLevel().intValue());

			StringWriter buffer = new StringWriter(80);

			// ADD TIMESTAMP
			if (dateTimeFormatter != null) {
				dateTimeFormatter.formatTo(record.getInstant(), buffer);
			}

			// BEGIN COLOR
			buffer.append(color.begin);

			// MESSAGE
			String message = record.getMessage();
			if (message != null && anonymize != null) {
				Matcher m = anonymize.matcher(message);
				while (m.find()) {
					m.appendReplacement(buffer.getBuffer(), "");
				}
				m.appendTail(buffer.getBuffer());
			} else {
				buffer.append(message);
			}

			// STACKTRACE
			Throwable thrown = record.getThrown();
			if (thrown != null) {
				buffer.append(System.lineSeparator());
				StackTraceUtils.deepSanitize(thrown).printStackTrace(new PrintWriter(buffer));
			}

			// END COLOR
			return buffer.append(color.end).append(System.lineSeparator()).toString();
		}

		public EscapeCode getColor(int level) {
			if (colorize) {
				if (level < Level.FINE.intValue())
					return EscapeCode.LIME_GREEN;
				if (level < Level.INFO.intValue())
					return EscapeCode.ROYAL_BLUE;
				if (level < Level.WARNING.intValue())
					return EscapeCode.NONE; // LOG INFO => NO COLOR
				if (level < Level.SEVERE.intValue())
					return EscapeCode.ORANGE_RED;

				return EscapeCode.CHERRY_RED; // SEVERE
			}

			return EscapeCode.NONE; // NO COLOR
		}

	}

	public static class ConsoleHandler extends Handler {

		@Override
		public void publish(LogRecord record) {
			// use either System.out or System.err depending on the severity of the error
			PrintStream out = record.getLevel().intValue() < Level.WARNING.intValue() ? System.out : System.err;

			// print messages to selected output stream
			out.print(getFormatter().format(record));

			// flush every message immediately
			out.flush();
		}

		@Override
		public void flush() {
			System.out.flush();
			System.err.flush();
		}

		@Override
		public void close() throws SecurityException {

		}

	}

	public static class EscapeCode {

		// see https://en.wikipedia.org/wiki/ANSI_escape_code#Colors
		public static final EscapeCode CHERRY_RED = newColorStyle(0xC4);
		public static final EscapeCode ORANGE_RED = newColorStyle(0xCA);
		public static final EscapeCode ROYAL_BLUE = newColorStyle(0x28);
		public static final EscapeCode LIME_GREEN = newColorStyle(0x27);

		public static final EscapeCode BOLD = newFontStyle(1);
		public static final EscapeCode ITALIC = newFontStyle(3);
		public static final EscapeCode UNDERLINE = newFontStyle(4);
		public static final EscapeCode STRIKEOUT = newFontStyle(9);

		public static final EscapeCode NONE = new EscapeCode("", "");

		public static EscapeCode newColorStyle(int color) {
			return new EscapeCode("38;5;" + color);
		}

		public static EscapeCode newFontStyle(int style) {
			return new EscapeCode("" + style);
		}

		public final String begin;
		public final String end;

		public EscapeCode(String code) {
			this("\u001b[" + code + "m", "\u001b[0m");
		}

		public EscapeCode(String begin, String end) {
			this.begin = begin;
			this.end = end;
		}

		public String apply(String text) {
			return begin + text + end;
		}

		public static boolean isSupported() {
			// see https://no-color.org and https://force-color.org
			if (isSet("NO_COLOR")) {
				return false;
			}
			if (isSet("FORCE_COLOR")) {
				return true;
			}

			// enable ANSI colors only if running with an interactive console (System.console() is never null since Java 22)
			if (System.console() == null || !System.console().isTerminal()) {
				return false;
			}

			// Windows 10 and later support ANSI escape codes once virtual terminal processing is enabled for the console
			if (File.separatorChar == '\\' && System.getenv("TERM") == null) {
				return enableVirtualTerminalProcessing();
			}

			// 256 color terminals
			String term = System.getenv("TERM");
			return (term != null && term.contains("256color")) || isSet("COLORTERM") || isSet("WT_SESSION");
		}

		private static boolean isSet(String variable) {
			String value = System.getenv(variable);
			return value != null && !value.isEmpty();
		}

		private static boolean enableVirtualTerminalProcessing() {
			try {
				boolean enabled = false;
				for (int handle : new int[] { Wincon.STD_OUTPUT_HANDLE, Wincon.STD_ERROR_HANDLE }) {
					HANDLE console = Kernel32.INSTANCE.GetStdHandle(handle);
					IntByReference mode = new IntByReference();
					if (console != null && Kernel32.INSTANCE.GetConsoleMode(console, mode)) {
						enabled |= (mode.getValue() & ENABLE_VIRTUAL_TERMINAL_PROCESSING) != 0 || Kernel32.INSTANCE.SetConsoleMode(console, mode.getValue() | ENABLE_VIRTUAL_TERMINAL_PROCESSING);
					}
				}
				return enabled;
			} catch (Throwable e) {
				return false; // JNA not available or legacy Windows console
			}
		}

		private static final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004;
	}

}
