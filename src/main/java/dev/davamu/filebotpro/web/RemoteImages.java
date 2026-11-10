package dev.davamu.filebotpro.web;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import dev.davamu.filebotpro.Resource;

/**
 * Downloads poster and artwork images whose URL comes from an online database. The URL (and every redirect) must point to a public web
 * server: a hostile or intercepted response must not make FileBot Pro request hosts of the local network (router, NAS, admin pages) or
 * read local files.
 */
public final class RemoteImages {

	/** Upper bound for a single image, the original size posters of TheMovieDB and TheTVDB are a few MB at most */
	public static final long MAX_IMAGE_BYTES = 32L * 1024 * 1024;

	private static final int MAX_REDIRECTS = 5;

	private static final Resource<HttpClient> client = Resource.lazy(() -> HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(20)).build());

	/**
	 * @return the image data, or null if the server has no image for this URL (e.g. 404)
	 */
	public static byte[] fetch(URI uri) throws IOException, InterruptedException {
		for (int i = 0; i <= MAX_REDIRECTS; i++) {
			requirePublicHttp(uri);

			HttpResponse<byte[]> response = getClient().send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).build(), BodyHandlers.ofByteArray());
			int status = response.statusCode();

			if (status >= 300 && status < 400) {
				String location = response.headers().firstValue("Location").orElseThrow(() -> new IOException("Redirect without location: " + response.uri()));
				uri = response.uri().resolve(location);
				continue;
			}

			if (status != 200) {
				return null;
			}

			String type = response.headers().firstValue("Content-Type").orElse("");
			if (!type.toLowerCase().startsWith("image/")) {
				throw new IOException("Not an image: " + type + " [" + uri + "]");
			}

			byte[] body = response.body();
			if (body.length > MAX_IMAGE_BYTES) {
				throw new IOException("Image too large: " + body.length + " bytes [" + uri + "]");
			}
			return body;
		}
		throw new IOException("Too many redirects: " + uri);
	}

	public static void requirePublicHttp(URI uri) throws IOException {
		String scheme = uri.getScheme();
		if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
			throw new IOException("Unsupported URL scheme: " + uri);
		}

		String host = uri.getHost();
		if (host == null || host.isEmpty()) {
			throw new IOException("URL without host: " + uri);
		}

		for (InetAddress address : InetAddress.getAllByName(host)) {
			if (!isPublic(address)) {
				throw new IOException("Refused to request local network address " + address.getHostAddress() + " [" + uri + "]");
			}
		}
	}

	public static boolean isPublic(InetAddress address) {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return false;
		}

		byte[] ip = address.getAddress();
		if (address instanceof Inet6Address) {
			// unique local addresses (fc00::/7)
			return (ip[0] & 0xFE) != 0xFC;
		}

		// carrier-grade NAT (100.64.0.0/10) and "this network" (0.0.0.0/8)
		int a = ip[0] & 0xFF, b = ip[1] & 0xFF;
		return !(a == 100 && b >= 64 && b <= 127) && a != 0;
	}

	private static HttpClient getClient() throws IOException {
		try {
			return client.get();
		} catch (Exception e) {
			throw new IOException("Failed to initialize HTTP client: " + e.getMessage(), e);
		}
	}

	private RemoteImages() {
		throw new UnsupportedOperationException();
	}

}
