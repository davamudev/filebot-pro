package dev.davamu.filebotpro.cli;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Arrays.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

import dev.davamu.filebotpro.Cache;
import dev.davamu.filebotpro.CacheType;

public enum ScriptSource {

	BUILT_IN {

		@Override
		public String accept(String input) {
			return input.startsWith("fn:") ? input.substring(3) : null;
		}

		@Override
		public ScriptProvider getScriptProvider(String input) throws Exception {
			return ScriptSource::getBuiltInScript;
		}
	},

	INLINE_GROOVY {

		@Override
		public String accept(String input) {
			return input.startsWith("g:") ? input.substring(2) : null;
		}

		@Override
		public ScriptProvider getScriptProvider(String input) throws Exception {
			return g -> g;
		}
	},

	REMOTE_URL {

		@Override
		public String accept(String input) {
			// absolute paths on Windows appear to be valid URIs so we need explicitly exclude them (e.g. C:\path\to\script.groovy)
			if (input.length() < 2 || input.charAt(1) == ':') {
				return null;
			}

			try {
				URI uri = new URI(input);
				if (uri.isAbsolute()) {
					// remote scripts are executed: only accept https (http only for loopback hosts, e.g. local development)
					String scheme = uri.getScheme();
					String host = uri.getHost();
					boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
					if (!"https".equalsIgnoreCase(scheme) && !("http".equalsIgnoreCase(scheme) && loopback)) {
						debug.warning("Rejected script URL (only https is allowed): " + input);
						return null;
					}
					return getName(new File(uri.getPath()));
				}
			} catch (Exception e) {
				debug.finest(e::toString);
			}
			return null;
		}

		@Override
		public ScriptProvider getScriptProvider(String input) throws Exception {
			URI parent = new URI(input).resolve(".");

			// key the cache by the full URL so scripts with the same name from different origins never share a cached copy
			return n -> getCache().text(parent.resolve(n + ".groovy").toString(), s -> new URI(s).toURL()).expire(Duration.ZERO).get();
		}
	},

	LOCAL_FILE {

		@Override
		public String accept(String input) {
			try {
				File f = new File(input).getCanonicalFile();
				if (f.isFile()) {
					return getName(f);
				}
			} catch (Exception e) {
				debug.finest(e::toString);
			}
			return null;
		}

		@Override
		public ScriptProvider getScriptProvider(String input) throws Exception {
			File base = new File(input).getCanonicalFile().getParentFile();

			return f -> readTextFile(new File(base, f + ".groovy"));
		}
	};

	/**
	 * Scripts implemented in Java (e.g. fn:sysinfo)
	 */
	private static final Map<String, String> JAVA_SCRIPTS = Map.of("sysinfo", "dev.davamu.filebotpro.cli.SystemInfo.print { println it }");

	/**
	 * Folder of the fn: scripts that ship with the application (GPL-3.0, see THIRD_PARTY_NOTICES.md)
	 */
	public static final String BUILT_IN_SCRIPTS = "/dev/davamu/filebotpro/scripts/";

	public static String getBuiltInScript(String name) throws Exception {
		String script = JAVA_SCRIPTS.get(name);
		if (script != null) {
			return script;
		}

		try (InputStream in = ScriptSource.class.getResourceAsStream(BUILT_IN_SCRIPTS + name + ".groovy")) {
			if (in == null) {
				throw new CmdlineException("Unknown script: fn:" + name);
			}
			return new String(in.readAllBytes(), UTF_8);
		}
	}

	public abstract String accept(String input);

	public abstract ScriptProvider getScriptProvider(String input) throws Exception;

	public Cache getCache() {
		return Cache.getCache(name(), CacheType.Persistent);
	}

	public static ScriptSource findScriptProvider(String input) throws Exception {
		return stream(values()).filter(s -> s.accept(input) != null).findFirst().orElseThrow(() -> {
			return new CmdlineException("Bad script source: " + input);
		});
	}

}
