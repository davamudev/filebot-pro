package dev.davamu.filebotpro.tools;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.imgscalr.Scalr;
import org.imgscalr.Scalr.Method;
import org.imgscalr.Scalr.Mode;

/**
 * Renders the application logos (PNG masters in the icons folder) into the formats used by the application and by jpackage:
 * <ul>
 * <li>Windows: multi-size .ico (PNG compressed entries)</li>
 * <li>macOS: .icns (PNG compressed entries)</li>
 * <li>Linux: 512px .png</li>
 * <li>Application: logo.png (icon), logo.full.png and logo.full.dark.png (logo with the name for light and dark themes) in the
 * resources folder</li>
 * <li>Windows installer: WiX UI bitmaps (welcome/finish dialog and top banner) in the installer folder</li>
 * </ul>
 *
 * usage: IconGenerator &lt;icons folder&gt; &lt;resources folder&gt; &lt;installer folder&gt; &lt;base name&gt;
 */
public class IconGenerator {

	public static void main(String[] args) throws Exception {
		Path folder = Paths.get(args[0]);
		Path resources = Paths.get(args[1]);
		Path installer = Paths.get(args[2]);
		String name = args[3];

		BufferedImage icon = read(folder.resolve("logo.png"));
		BufferedImage full = read(folder.resolve("logo.full.png"));
		BufferedImage fullDark = read(folder.resolve("logo.full.dark.png"));

		Files.write(folder.resolve(name + ".ico"), ico(icon, 16, 20, 24, 32, 40, 48, 64, 128, 256));
		Files.write(folder.resolve(name + ".icns"), icns(icon));
		Files.write(folder.resolve(name + ".png"), png(icon, 512, 512));

		Files.write(resources.resolve("logo.png"), png(icon, 512, 512));
		Files.write(resources.resolve("logo.full.png"), png(full, 512 * full.getWidth() / full.getHeight(), 512));
		Files.write(resources.resolve("logo.full.dark.png"), png(fullDark, 512 * fullDark.getWidth() / fullDark.getHeight(), 512));

		Files.createDirectories(installer);
		Files.write(installer.resolve("installer-dialog.bmp"), bmp(installerDialog(fullDark)));
		Files.write(installer.resolve("installer-banner.bmp"), bmp(installerBanner(icon)));

		System.out.println("Icons written to " + folder.toAbsolutePath() + ", " + resources.toAbsolutePath() + " and " + installer.toAbsolutePath());
	}

	private static BufferedImage read(Path file) throws IOException {
		BufferedImage image = ImageIO.read(file.toFile());
		if (image == null) {
			throw new IOException("Not an image: " + file);
		}
		return image;
	}

	private static byte[] png(BufferedImage image, int width, int height) throws IOException {
		BufferedImage scaled = image.getWidth() == width && image.getHeight() == height ? image : Scalr.resize(image, Method.ULTRA_QUALITY, Mode.FIT_EXACT, width, height);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(scaled, "png", out);
		return out.toByteArray();
	}

	/**
	 * WixUI welcome / finish dialog background (493x312): dark side panel with the full logo, the dialog text is drawn on the white
	 * area to the right of it.
	 */
	private static BufferedImage installerDialog(BufferedImage logo) {
		int width = 493, height = 312, panel = 164;

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = graphics(image);
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);
		g.setPaint(new GradientPaint(0, 0, new Color(0x232328), 0, height, new Color(0x141417)));
		g.fillRect(0, 0, panel, height);
		g.setColor(new Color(0xF97316));
		g.fillRect(panel - 3, 0, 3, height);
		drawFit(g, logo, 0, 0, panel - 3, height, 18);
		g.dispose();
		return image;
	}

	/**
	 * WixUI top banner (493x58): the dialog title is drawn in black on the left, the icon sits on the right.
	 */
	private static BufferedImage installerBanner(BufferedImage icon) {
		int width = 493, height = 58;

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = graphics(image);
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);
		drawFit(g, icon, width - height - 4, 0, height, height, 5);
		g.dispose();
		return image;
	}

	private static Graphics2D graphics(BufferedImage image) {
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		return g;
	}

	/**
	 * Draws the image centered in the given box, scaled to fit inside the margin while keeping its aspect ratio.
	 */
	private static void drawFit(Graphics2D g, BufferedImage image, int x, int y, int width, int height, int margin) {
		double scale = Math.min((width - 2.0 * margin) / image.getWidth(), (height - 2.0 * margin) / image.getHeight());
		int w = (int) Math.round(image.getWidth() * scale);
		int h = (int) Math.round(image.getHeight() * scale);
		BufferedImage scaled = Scalr.resize(image, Method.ULTRA_QUALITY, Mode.FIT_EXACT, w, h);
		g.drawImage(scaled, x + (width - w) / 2, y + (height - h) / 2, null);
	}

	/**
	 * 24-bit BMP for the WixUI bitmap controls.
	 */
	private static byte[] bmp(BufferedImage image) throws IOException {
		BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D g = rgb.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (!ImageIO.write(rgb, "bmp", out)) {
			throw new IOException("BMP writer not available");
		}
		return out.toByteArray();
	}

	/**
	 * ICO container with PNG compressed images (supported since Windows Vista).
	 */
	private static byte[] ico(BufferedImage icon, int... sizes) throws IOException {
		byte[][] images = new byte[sizes.length][];
		for (int i = 0; i < sizes.length; i++) {
			images[i] = png(icon, sizes[i], sizes[i]);
		}

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		LittleEndian out = new LittleEndian(buffer);

		// ICONDIR
		out.short16(0);
		out.short16(1);
		out.short16(sizes.length);

		// ICONDIRENTRY
		int offset = 6 + 16 * sizes.length;
		for (int i = 0; i < sizes.length; i++) {
			out.byte8(sizes[i] >= 256 ? 0 : sizes[i]);
			out.byte8(sizes[i] >= 256 ? 0 : sizes[i]);
			out.byte8(0); // no palette
			out.byte8(0); // reserved
			out.short16(1); // color planes
			out.short16(32); // bits per pixel
			out.int32(images[i].length);
			out.int32(offset);
			offset += images[i].length;
		}

		for (byte[] image : images) {
			buffer.write(image);
		}

		return buffer.toByteArray();
	}

	/**
	 * ICNS container with PNG compressed images (macOS 10.7+).
	 */
	private static byte[] icns(BufferedImage icon) throws IOException {
		Map<String, Integer> types = new LinkedHashMap<String, Integer>();
		types.put("icp4", 16);
		types.put("icp5", 32);
		types.put("ic11", 32);
		types.put("icp6", 64);
		types.put("ic12", 64);
		types.put("ic07", 128);
		types.put("ic08", 256);
		types.put("ic13", 256);
		types.put("ic09", 512);
		types.put("ic14", 512);
		types.put("ic10", 1024);

		ByteArrayOutputStream entries = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(entries);
		for (Map.Entry<String, Integer> it : types.entrySet()) {
			byte[] image = png(icon, it.getValue(), it.getValue());
			out.writeBytes(it.getKey());
			out.writeInt(image.length + 8);
			out.write(image);
		}

		ByteArrayOutputStream icns = new ByteArrayOutputStream();
		DataOutputStream header = new DataOutputStream(icns);
		header.writeBytes("icns");
		header.writeInt(entries.size() + 8);
		entries.writeTo(icns);
		return icns.toByteArray();
	}

	private static class LittleEndian {

		private final ByteArrayOutputStream out;

		public LittleEndian(ByteArrayOutputStream out) {
			this.out = out;
		}

		public void byte8(int value) {
			out.write(value & 0xFF);
		}

		public void short16(int value) {
			out.write(value & 0xFF);
			out.write((value >> 8) & 0xFF);
		}

		public void int32(int value) {
			short16(value & 0xFFFF);
			short16((value >> 16) & 0xFFFF);
		}
	}

}
