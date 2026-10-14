package dev.davamu.filebotpro;

import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.ResourceManager.*;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import dev.davamu.filebotpro.web.HttpClients;
import dev.davamu.filebotpro.web.SearchResult;
import dev.davamu.filebotpro.web.ThumbnailProvider.ResolutionVariant;

/**
 * Poster thumbnails for the selection dialogs, downloaded from the image server of each database. The poster of each option is usually
 * known from the search itself; options from the local index, nfo files or xattr need one (cached) API request for their record. Then one
 * small image per option that is shown, cached for 6 months.
 */
public final class ThumbnailServices {

	private static final int SIZE = 48;

	// options further down the list rarely matter, and each unknown poster costs one API request
	private static final int MAX_OPTIONS = 30;

	public static Map<SearchResult, Icon> getThumbnails(List<? extends SearchResult> keys, ResolutionVariant variant, Function<SearchResult, URI> poster) throws Exception {
		Cache cache = Cache.getCache("thumbnail_" + variant.ordinal(), CacheType.Persistent);

		Map<SearchResult, byte[]> images = new LinkedHashMap<SearchResult, byte[]>();
		Map<URI, SearchResult> requested = new LinkedHashMap<URI, SearchResult>();
		Map<URI, CompletableFuture<HttpResponse<byte[]>>> requests = new LinkedHashMap<URI, CompletableFuture<HttpResponse<byte[]>>>();

		// posters that were not part of a search need a lookup each, so resolve them in parallel and only for the first options
		Map<SearchResult, CompletableFuture<URI>> posters = new LinkedHashMap<SearchResult, CompletableFuture<URI>>();
		keys.stream().limit(MAX_OPTIONS).forEach(key -> posters.put(key, CompletableFuture.supplyAsync(() -> poster.apply(key), WebServices.requestThreadPool)));

		for (SearchResult key : posters.keySet()) {
			URI image = posters.get(key).join();
			if (image != null) {
				byte[] bytes = (byte[]) cache.get(image.toString());
				if (bytes != null) {
					images.put(key, bytes);
				} else if (!requests.containsKey(image)) {
					requested.put(image, key);
					requests.put(image, HttpClients.get().sendAsync(HttpRequest.newBuilder(image).timeout(Duration.ofSeconds(20)).build(), BodyHandlers.ofByteArray()));
					debug.fine(format("Request %s", image));
				}
			}
		}

		requests.forEach((image, request) -> {
			try {
				HttpResponse<byte[]> response = request.get();
				if (response.statusCode() != 200) {
					// do not cache failures (the thumbnail would stay empty for months)
					images.put(requested.get(image), new byte[0]);
					return;
				}
				byte[] bytes = response.body();
				cache.put(image.toString(), bytes);
				images.put(requested.get(image), bytes);
			} catch (Exception e) {
				debug.warning(e::toString);
			}
		});

		Map<SearchResult, Icon> icons = new HashMap<SearchResult, Icon>(images.size());
		images.forEach((key, bytes) -> {
			if (bytes.length > 0) {
				try {
					BufferedImage thumbnail = square(read(bytes, SIZE * variant.scaleFactor), SIZE * variant.scaleFactor);
					icons.put(key, new ImageIcon(getMultiResolutionImage(thumbnail, variant.scaleFactor)));
				} catch (Exception e) {
					debug.log(Level.SEVERE, e, e::toString);
				}
			}
		});
		return icons;
	}

	/**
	 * Decode the image with subsampling, so large posters (e.g. original size fallbacks) never need more memory than
	 * about twice the thumbnail size.
	 */
	private static BufferedImage read(byte[] bytes, int size) throws IOException {
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw new IOException("Unsupported image format");
			}

			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				int side = Math.min(reader.getWidth(0), reader.getHeight(0));
				int subsampling = Math.max(1, side / (2 * size));

				ImageReadParam param = reader.getDefaultReadParam();
				param.setSourceSubsampling(subsampling, subsampling, 0, 0);
				return reader.read(0, param);
			} finally {
				reader.dispose();
			}
		}
	}

	/**
	 * Center square of the poster, scaled to the given size.
	 */
	private static BufferedImage square(BufferedImage image, int size) {
		int side = Math.min(image.getWidth(), image.getHeight());
		BufferedImage crop = image.getSubimage((image.getWidth() - side) / 2, (image.getHeight() - side) / 2, side, side);

		BufferedImage thumbnail = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = thumbnail.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(crop, 0, 0, size, size, null);
		g.dispose();
		return thumbnail;
	}

	private ThumbnailServices() {
		throw new UnsupportedOperationException();
	}

}
