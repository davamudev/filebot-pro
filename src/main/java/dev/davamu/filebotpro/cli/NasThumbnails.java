package dev.davamu.filebotpro.cli;

import static dev.davamu.filebotpro.Logging.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.imgscalr.Scalr;
import org.imgscalr.Scalr.Method;
import org.imgscalr.Scalr.Mode;

import dev.davamu.filebotpro.web.WebRequest;

/**
 * Video thumbnails for the file managers and media servers of Synology NAS (File Station, DS File, Media Server) and QNAP NAS (File
 * Station, Qfile), which only show thumbnails for video files if the thumbnail files already exist
 */
public class NasThumbnails {

	public enum Layout {

		/**
		 * @eaDir/Movie.mkv/SYNOVIDEO_VIDEO_SCREENSHOT.jpg (same as Video Station)
		 */
		Synology,

		/**
		 * .@__thumb/s800Movie.mkv, .@__thumb/s100Movie.mkv and .@__thumb/defaultMovie.mkv (same as Multimedia Console)
		 */
		QNAP;

		public boolean isDetected(File folder) {
			switch (this) {
			case Synology:
				return new File("/etc/synoinfo.conf").isFile() || new File(folder, "@eaDir").isDirectory();
			default:
				return new File("/etc/config/uLinux.conf").isFile() || new File(folder, ".@__thumb").isDirectory();
			}
		}
	}

	/**
	 * @return number of thumbnail layouts that have been written (none if neither a Synology NAS nor a QNAP NAS is detected)
	 */
	public static int write(File video, BufferedImage image) throws IOException {
		File folder = video.getParentFile();
		int count = 0;

		if (Layout.Synology.isDetected(folder)) {
			File dir = new File(new File(folder, "@eaDir"), video.getName());
			writeImage(scale(image, 852, 480, false), new File(dir, "SYNOVIDEO_VIDEO_SCREENSHOT.jpg"), video);
			count++;
		}

		if (Layout.QNAP.isDetected(folder)) {
			File dir = new File(folder, ".@__thumb");
			writeImage(scale(image, 800, 800, true), new File(dir, "s800" + video.getName()), video);
			writeImage(scale(image, 260, 260, false), new File(dir, "s100" + video.getName()), video);
			writeImage(scale(image, 400, 400, true), new File(dir, "default" + video.getName()), video);
			count++;
		}

		return count;
	}

	public static BufferedImage read(File image) throws IOException {
		BufferedImage source = ImageIO.read(image);
		if (source == null) {
			throw new IOException("Unsupported image: " + image);
		}
		return source;
	}

	public static BufferedImage read(URL image) throws IOException {
		ByteBuffer data = WebRequest.fetch(image);
		byte[] bytes = new byte[data.remaining()];
		data.get(bytes);

		BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
		if (source == null) {
			throw new IOException("Unsupported image: " + image);
		}
		return source;
	}

	/**
	 * @param cover
	 *            true to cover the given size (i.e. the smaller side matches the box), false to fit into the given size
	 */
	private static BufferedImage scale(BufferedImage image, int width, int height, boolean cover) {
		double ratio = cover ? Math.max((double) width / image.getWidth(), (double) height / image.getHeight()) : Math.min((double) width / image.getWidth(), (double) height / image.getHeight());
		int w = Math.max(1, (int) Math.round(image.getWidth() * ratio));
		int h = Math.max(1, (int) Math.round(image.getHeight() * ratio));
		BufferedImage scaled = Scalr.resize(image, Method.QUALITY, Mode.FIT_EXACT, w, h);

		// JPEG has no alpha channel
		BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		rgb.createGraphics().drawImage(scaled, 0, 0, null);
		return rgb;
	}

	private static void writeImage(BufferedImage image, File file, File video) throws IOException {
		Files.createDirectories(file.getParentFile().toPath());

		// overwrite any existing (e.g. larger) thumbnail
		Files.deleteIfExists(file.toPath());

		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
		try (ImageOutputStream output = ImageIO.createImageOutputStream(file)) {
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(0.85f);
			writer.setOutput(output);
			writer.write(null, new IIOImage(image, null, null), param);
		} finally {
			writer.dispose();
		}

		// same last modified time as the video file, so the thumbnail is not considered outdated
		try {
			Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(video.lastModified()));
		} catch (Exception e) {
			debug.finest(e::toString);
		}

		log.finest(String.format("[thumbnail] Write [%s]", file));
	}

}
