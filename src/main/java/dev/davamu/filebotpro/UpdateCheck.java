package dev.davamu.filebotpro;

import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.Settings.*;
import static dev.davamu.filebotpro.util.XPathUtilities.*;
import static java.util.stream.Collectors.*;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * New release notice, based on the update.xml descriptor attached to every GitHub release (revision, version, title, message,
 * download and the CHANGELOG.md sections of the last releases). Only notifies: updating is left to the user (installer,
 * package manager, docker pull, ...).
 */
public final class UpdateCheck {

	public record Update(int revision, String version, String title, String message, String download, List<Release> releases) {
	}

	public record Release(String version, String notes) {
	}

	private static final String PREF_ENABLED = "update.check";
	private static final String PREF_IGNORE = "update.ignore";
	private static final String PREF_NOTICE = "update.notice";

	/**
	 * Update descriptor configured at build time and not disabled with -Dapplication.update=skip
	 */
	public static boolean isAvailable() {
		return !"skip".equals(System.getProperty("application.update")) && !getApplicationProperty("update.url").isEmpty();
	}

	/**
	 * Available and not turned off by the user (help menu)
	 */
	public static boolean isEnabled() {
		return isAvailable() && !"false".equals(prefs().get(PREF_ENABLED));
	}

	public static void setEnabled(boolean enabled) {
		prefs().put(PREF_ENABLED, enabled ? null : "false");
	}

	public static Update getLatest() throws Exception {
		Cache cache = Cache.getCache(getApplicationName(), CacheType.Persistent);
		Document dom = cache.xml(getApplicationProperty("update.url"), url -> URI.create(url).toURL()).expire(Cache.ONE_DAY).retry(0).get();
		cache.flush();

		Map<String, String> fields = streamElements(dom.getFirstChild()).collect(toMap(n -> n.getNodeName(), n -> n.getTextContent().trim(), (a, b) -> a));
		List<Release> releases = streamElements(dom.getFirstChild()).filter(n -> n.getNodeName().equals("release")).map(n -> new Release(((Element) n).getAttribute("version"), n.getTextContent().trim())).toList();
		return new Update(Integer.parseInt(fields.get("revision")), fields.getOrDefault("version", ""), fields.get("title"), fields.getOrDefault("message", fields.get("title")), fields.get("download"), releases);
	}

	/**
	 * Release notes of the versions after this one, newest first
	 */
	public static List<Release> getChanges(Update update) {
		return update.releases().stream().filter(r -> compareVersion(r.version(), getApplicationVersion()) > 0).toList();
	}

	private static int compareVersion(String a, String b) {
		String[] x = a.split("\\D+"), y = b.split("\\D+");
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int c = Integer.compare(i < x.length && !x[i].isEmpty() ? Integer.parseInt(x[i]) : 0, i < y.length && !y[i].isEmpty() ? Integer.parseInt(y[i]) : 0);
			if (c != 0) {
				return c;
			}
		}
		return 0;
	}

	private static final Pattern CODE = Pattern.compile("`([^`]+)`");
	private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
	private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");

	/**
	 * CHANGELOG.md sections as simple HTML body content for Swing: ### headings, - lists, `code`, **bold** and [links](url).
	 * Version headings only when there is more than one release.
	 */
	public static String toHTML(List<Release> releases) {
		StringBuilder html = new StringBuilder();
		for (Release release : releases) {
			if (releases.size() > 1) {
				html.append("<p class=\"version\">").append(escape(release.version())).append("</p>");
			}
			boolean list = false;
			for (String line : release.notes().split("\\R")) {
				line = line.strip();
				if (line.startsWith("- ")) {
					html.append(list ? "" : "<ul>").append("<li>").append(inline(line.substring(2))).append("</li>");
					list = true;
					continue;
				}
				if (list) {
					html.append("</ul>");
					list = false;
				}
				if (line.startsWith("#")) {
					html.append("<p class=\"section\">").append(inline(line.replaceFirst("^#+\\s*", ""))).append("</p>");
				} else if (!line.isEmpty()) {
					html.append("<p>").append(inline(line)).append("</p>");
				}
			}
			html.append(list ? "</ul>" : "");
		}
		return html.toString();
	}

	private static String inline(String text) {
		text = CODE.matcher(escape(text)).replaceAll("<code>&nbsp;$1&nbsp;</code>");
		text = BOLD.matcher(text).replaceAll("<b>$1</b>");
		return LINK.matcher(text).replaceAll("<a href=\"$2\">$1</a>");
	}

	private static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/**
	 * Newer release than this build (development builds without a revision never ask)
	 */
	public static boolean isNewer(Update update) {
		int current = getApplicationRevisionNumber();
		return current > 0 && update.revision() > current;
	}

	/**
	 * Newer release that the user has not chosen to skip
	 */
	public static Update getAvailableUpdate() throws Exception {
		Update update = getLatest();
		return isNewer(update) && update.revision() > getIgnoredRevision() ? update : null;
	}

	public static void ignore(Update update) {
		prefs().put(PREF_IGNORE, String.valueOf(update.revision()));
	}

	private static int getIgnoredRevision() {
		try {
			return Integer.parseInt(prefs().get(PREF_IGNORE, "0"));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * How to update this kind of installation
	 */
	public static String getInstructions() {
		return switch (getApplicationDeployment()) {
			case "msi" -> "Download the new installer and run it, it replaces the installed version.";
			case "zip", "tar" -> "Download the new portable archive and replace the application folder with it.";
			case "app" -> "Download the new disk image and replace the application in the Applications folder.";
			case "deb" -> "Download the new package and install it with: sudo apt install ./<package>.deb";
			case "rpm" -> "Download the new package and install it with: sudo dnf install ./<package>.rpm";
			case "spk" -> "Download the new .spk and install it in Package Center (Manual Install).";
			case "docker" -> "Pull the new image and recreate the container: docker compose pull && docker compose up -d";
			default -> "";
		};
	}

	/**
	 * Command-line notice on stderr, so the output of the command stays untouched, at most once a day. Docker prints it right
	 * away to the container log, other installations after the command and only in an interactive console. Returns the action
	 * to run after the command.
	 */
	public static Runnable startConsoleNotice() {
		boolean docker = "docker".equals(getApplicationDeployment());
		boolean interactive = System.console() != null && System.console().isTerminal();

		if (!isEnabled() || !(docker || interactive) || !isNoticeDue()) {
			return () -> {
			};
		}

		CompletableFuture<Update> check = CompletableFuture.supplyAsync(() -> {
			try {
				return getAvailableUpdate();
			} catch (Exception e) {
				debug.log(Level.FINE, "Failed to check for updates", e);
				return null;
			}
		});

		// Docker: print as soon as known (fn:watcher runs for days), short commands wait for it like everywhere else
		if (docker) {
			CompletableFuture<Void> printed = check.thenAccept(UpdateCheck::printNotice);
			return () -> {
				try {
					printed.get(3, TimeUnit.SECONDS);
				} catch (Exception e) {
					debug.log(Level.FINE, "Update check did not finish in time", e);
				}
			};
		}

		return () -> {
			try {
				printNotice(check.get(3, TimeUnit.SECONDS));
			} catch (Exception e) {
				debug.log(Level.FINE, "Update check did not finish in time", e);
			}
		};
	}

	private static boolean isNoticeDue() {
		try {
			return Instant.parse(prefs().get(PREF_NOTICE, Instant.EPOCH.toString())).plus(Duration.ofDays(1)).isBefore(Instant.now());
		} catch (Exception e) {
			return true;
		}
	}

	private static void printNotice(Update update) {
		if (update == null) {
			return;
		}

		prefs().put(PREF_NOTICE, Instant.now().toString());

		System.err.format("%n%s is available: %s%n", update.title(), update.download());
		String instructions = getInstructions();
		if (!instructions.isEmpty()) {
			System.err.println(instructions);
		}
		System.err.println("Turn this notice off with application.update=skip in " + ApplicationFolder.AppData.resolve("system.properties"));
		System.err.flush();
	}

	private static Settings prefs() {
		return Settings.forPackage(UpdateCheck.class);
	}

	private UpdateCheck() {
		throw new UnsupportedOperationException();
	}

}
