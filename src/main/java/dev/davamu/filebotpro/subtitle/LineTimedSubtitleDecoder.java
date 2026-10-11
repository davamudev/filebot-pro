package dev.davamu.filebotpro.subtitle;

import static dev.davamu.filebotpro.util.RegularExpressions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Decoder for line based subtitle formats that may only specify the start time of each line (e.g. TMPlayer or MPL2), so the end time defaults to the
 * start time of the next line, or a few seconds after the start time for the last line or long pauses.
 */
public abstract class LineTimedSubtitleDecoder implements SubtitleDecoder {

	public static final long MAX_DURATION = 5000;

	private final Pattern line;

	protected LineTimedSubtitleDecoder(Pattern line) {
		this.line = line;
	}

	/**
	 * @return start time in milliseconds
	 */
	protected abstract long getStart(Matcher m);

	/**
	 * @return end time in milliseconds, or -1 if undefined
	 */
	protected abstract long getEnd(Matcher m);

	protected abstract String getText(Matcher m);

	@Override
	public Stream<SubtitleElement> decode(String content) {
		List<long[]> times = new ArrayList<long[]>();
		List<String> texts = new ArrayList<String>();

		NEWLINE.splitAsStream(content).map(String::trim).forEach(s -> {
			Matcher m = line.matcher(s);
			if (m.matches()) {
				String text = getText(m).trim();
				if (text.length() > 0) {
					times.add(new long[] { getStart(m), getEnd(m) });
					texts.add(text);
				}
			}
		});

		List<SubtitleElement> elements = new ArrayList<SubtitleElement>(texts.size());
		for (int i = 0; i < texts.size(); i++) {
			long start = times.get(i)[0];
			long end = times.get(i)[1];

			if (end <= start) {
				long next = i + 1 < times.size() ? times.get(i + 1)[0] : Long.MAX_VALUE;
				end = Math.min(next, start + MAX_DURATION);
			}

			// translate '|' to new lines
			elements.add(new SubtitleElement(start, end, String.join("\n", PIPE.split(texts.get(i)))));
		}
		return elements.stream();
	}

	/**
	 * TMPlayer, e.g. 00:01:23:Hello|World or 0:01:23=Hello
	 */
	public static class TMPlayer extends LineTimedSubtitleDecoder {

		public TMPlayer() {
			super(Pattern.compile("(\\d{1,2}):(\\d{2}):(\\d{2})[:=](.*)"));
		}

		@Override
		protected long getStart(Matcher m) {
			return ((Long.parseLong(m.group(1)) * 60 + Long.parseLong(m.group(2))) * 60 + Long.parseLong(m.group(3))) * 1000;
		}

		@Override
		protected long getEnd(Matcher m) {
			return -1;
		}

		@Override
		protected String getText(Matcher m) {
			return m.group(4);
		}
	}

	/**
	 * MPL2, e.g. [123][160]Hello|/World with times in deciseconds and / for italic lines
	 */
	public static class MPL2 extends LineTimedSubtitleDecoder {

		public MPL2() {
			super(Pattern.compile("\\[(\\d+)\\]\\[(\\d*)\\](.*)"));
		}

		@Override
		protected long getStart(Matcher m) {
			return Long.parseLong(m.group(1)) * 100;
		}

		@Override
		protected long getEnd(Matcher m) {
			return m.group(2).isEmpty() ? -1 : Long.parseLong(m.group(2)) * 100;
		}

		@Override
		protected String getText(Matcher m) {
			// italic lines start with /
			return PIPE.splitAsStream(m.group(3)).map(s -> s.startsWith("/") ? "<i>" + s.substring(1).trim() + "</i>" : s).reduce((a, b) -> a + "|" + b).orElse("");
		}
	}

}
