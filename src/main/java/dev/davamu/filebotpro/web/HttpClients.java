package dev.davamu.filebotpro.web;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

import dev.davamu.filebotpro.Resource;

/**
 * Shared java.net.http.HttpClient (HTTP/2) instance. The client is created on first use, so that a client that cannot be created (e.g. a firewall
 * blocking loopback connections on Windows) only breaks the services that need it, and creation is retried on the next request.
 */
public final class HttpClients {

	private static final Resource<HttpClient> client = Resource.lazy(() -> HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build());

	public static HttpClient get() throws IOException {
		try {
			return client.get();
		} catch (Exception e) {
			// e.g. java.io.IOException: Unable to establish loopback connection
			String message = "Failed to initialize HTTP client: " + e.getMessage();
			if (System.getProperty("os.name", "").startsWith("Windows")) {
				message += " (firewall or antivirus software may be blocking loopback connections to 127.0.0.1)";
			}
			throw new IOException(message, e);
		}
	}

	private HttpClients() {
		throw new UnsupportedOperationException();
	}

}
