package dev.davamu.filebotpro.cli;

import static java.util.stream.Collectors.*;
import static dev.davamu.filebotpro.MediaTypes.*;
import static dev.davamu.filebotpro.WebServices.*;
import static dev.davamu.filebotpro.media.XattrMetaInfo.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.imgscalr.Scalr;
import org.imgscalr.Scalr.Method;
import org.imgscalr.Scalr.Mode;

import dev.davamu.filebotpro.web.Artwork;
import dev.davamu.filebotpro.web.Episode;
import dev.davamu.filebotpro.web.Movie;
import dev.davamu.filebotpro.web.SeriesInfo;

/**
 * Cover image of a media file, as written by --apply cover and attached by --apply metadata
 */
public class CoverArt {

	public static final String NAME = "cover";

	private static final String[] EXTENSIONS = { "jpg", "jpeg", "png" };

	/**
	 * Cover file to write for the given media file: cover.jpg for the whole folder, or name-cover.jpg if the folder also
	 * holds other movies or other seasons
	 */
	public static File getCoverFile(File file, Object metadata, String extension) {
		File folder = file.getParentFile();
		if (isSharedFolder(file, metadata)) {
			return new File(folder, getNameWithoutExtension(file.getName()) + '-' + NAME + '.' + extension);
		}
		return new File(folder, NAME + '.' + extension);
	}

	/**
	 * Existing image for the media file: its own cover or poster first, then the cover or poster of the folder (unless
	 * the folder holds other movies or seasons), then the series poster for episodes
	 */
	public static File findLocal(File file, Object metadata) {
		List<File> candidates = new ArrayList<File>();
		File folder = file.getParentFile();
		String name = getNameWithoutExtension(file.getName());

		addImages(candidates, folder, name + '-' + NAME, name + "-poster");

		if (!isSharedFolder(file, metadata)) {
			if (metadata instanceof Episode) {
				addImages(candidates, folder, NAME, "season", "poster", "folder");
				addImages(candidates, folder.getParentFile(), "poster", "folder", NAME);
			} else {
				addImages(candidates, folder, NAME, "poster", "folder");
			}
		}

		return candidates.stream().filter(File::isFile).findFirst().orElse(null);
	}

	/**
	 * Online images in order of preference: movie posters, or season posters then series posters (preferred language
	 * first, then artwork without text, then everything else)
	 */
	public static List<URL> findRemote(Object metadata) throws Exception {
		List<Artwork> artwork = new ArrayList<Artwork>();

		if (metadata instanceof Movie) {
			Movie movie = (Movie) metadata;
			artwork.addAll(MetadataMethods.getArtwork(movie, "posters", movie.getLanguage()));
		} else if (metadata instanceof Episode) {
			Episode episode = (Episode) metadata;
			SeriesInfo series = episode.getSeriesInfo();
			Integer season = getSeason(episode);

			if (series != null && series.getId() != null) {
				if (TheTVDB.getIdentifier().equalsIgnoreCase(series.getDatabase())) {
					if (season != null) {
						MetadataMethods.getArtwork(series, "season", series.getLanguage()).stream().filter(a -> isSeason(a, season)).forEach(artwork::add);
					}
					artwork.addAll(MetadataMethods.getArtwork(series, "poster", series.getLanguage()));
				} else if (TheMovieDB_TV.getIdentifier().equalsIgnoreCase(series.getDatabase())) {
					if (season != null) {
						artwork.addAll(MetadataMethods.getArtwork(series, "season/" + season, series.getLanguage()));
					}
					artwork.addAll(MetadataMethods.getArtwork(series, "posters", series.getLanguage()));
				}
			}
		}

		return artwork.stream().map(Artwork::getUrl).filter(Objects::nonNull).distinct().collect(toList());
	}

	// embedded covers are shown as thumbnails by players and media servers, so the original poster (often 2000x3000
	// and 1 MB) would only waste space in every file
	private static final int ATTACHMENT_MAX_HEIGHT = 1000;
	private static final long ATTACHMENT_MAX_BYTES = 300 * 1024;

	/**
	 * Cover image to embed into media files: the image itself if it is small enough, or a temporary copy scaled down to
	 * 1000 pixels in height (the caller deletes files that are not the given image)
	 */
	public static File getAttachment(File image) throws Exception {
		if (image.length() <= ATTACHMENT_MAX_BYTES) {
			return image;
		}

		BufferedImage source = ImageIO.read(image);
		if (source == null || source.getHeight() <= ATTACHMENT_MAX_HEIGHT && image.length() <= 2 * ATTACHMENT_MAX_BYTES) {
			return image;
		}

		int height = Math.min(ATTACHMENT_MAX_HEIGHT, source.getHeight());
		int width = Math.max(1, source.getWidth() * height / source.getHeight());
		BufferedImage scaled = Scalr.resize(source, Method.QUALITY, Mode.FIT_EXACT, width, height);

		// JPEG has no alpha channel
		BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		rgb.createGraphics().drawImage(scaled, 0, 0, null);

		File attachment = File.createTempFile(NAME, ".jpg");
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
		try (ImageOutputStream output = ImageIO.createImageOutputStream(attachment)) {
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(0.9f);
			writer.setOutput(output);
			writer.write(null, new IIOImage(rgb, null, null), param);
		} finally {
			writer.dispose();
		}
		return attachment;
	}

	public static String getMimeType(File image) {
		return hasExtension(image, "png") ? "image/png" : "image/jpeg";
	}

	/**
	 * TheTVDB season artwork is tagged [season, number, resolution, ...]
	 */
	public static boolean isSeason(Artwork artwork, int season) {
		List<String> tags = artwork.getTags();
		return tags.size() > 1 && tags.get(1).equals(String.valueOf(season));
	}

	private static Integer getSeason(Episode episode) {
		return episode.getSpecial() != null ? Integer.valueOf(0) : episode.getSeason();
	}

	/**
	 * Folder that also holds videos of other movies, series or seasons (e.g. all movies in one folder)
	 */
	private static boolean isSharedFolder(File file, Object metadata) {
		Object key = getCoverKey(metadata);
		File[] siblings = file.getParentFile().listFiles(f -> !f.equals(file) && VIDEO_FILES.accept(f));
		if (key == null || siblings == null) {
			return false;
		}

		for (File f : siblings) {
			Object other = getCoverKey(xattr.getMetaInfo(f));
			if (other != null && !other.equals(key)) {
				return true;
			}
		}
		return false;
	}

	private static Object getCoverKey(Object metadata) {
		if (metadata instanceof Movie) {
			Movie movie = (Movie) metadata;
			return movie.getTmdbId() > 0 ? "tmdb:" + movie.getTmdbId() : movie.getImdbId() > 0 ? "imdb:" + movie.getImdbId() : movie.getNameWithYear();
		}
		if (metadata instanceof Episode) {
			Episode episode = (Episode) metadata;
			return episode.getSeriesName() + "::" + getSeason(episode);
		}
		return null;
	}

	private static void addImages(List<File> candidates, File folder, String... names) {
		if (folder == null) {
			return;
		}
		for (String name : names) {
			for (String extension : EXTENSIONS) {
				candidates.add(new File(folder, name + '.' + extension));
			}
		}
	}

	private CoverArt() {
		throw new UnsupportedOperationException();
	}

}
