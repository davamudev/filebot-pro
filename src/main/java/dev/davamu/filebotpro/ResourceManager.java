package dev.davamu.filebotpro;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.image.AbstractMultiResolutionImage;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.UIManager;

import org.imgscalr.Scalr;
import org.imgscalr.Scalr.Method;
import org.imgscalr.Scalr.Mode;

import com.formdev.flatlaf.extras.FlatSVGIcon;

public final class ResourceManager {

	private static final Map<String, Icon> cache = synchronizedMap(new HashMap<String, Icon>(256));

	public static Icon getIcon(String name) {
		return cache.computeIfAbsent(name, i -> {
			// prefer scalable monochrome symbols (see resources/svg/icons.properties)
			Icon symbol = getSymbolIcon(i);
			if (symbol != null) {
				return symbol;
			}

			// prefer scalable colored icons (resources/{name}.svg)
			if (ResourceManager.class.getResource("resources/" + i + ".svg") != null) {
				return new FlatSVGIcon(RESOURCE_FOLDER + i + ".svg", 16, 16);
			}

			// load image
			URL[] resource = getMultiResolutionImageResource(i);
			if (resource.length > 0) {
				return new ImageIcon(getMultiResolutionImage(resource));
			}

			// default image
			return null;
		});
	}

	public static List<Image> getApplicationIconImages() {
		BufferedImage logo = getLogoImage("logo");
		return IntStream.of(16, 20, 24, 28, 32, 40, 48, 64, 128, 256).mapToObj(size -> (Image) resize(logo, size, size)).toList();
	}

	/**
	 * Application icon (resources/logo.png, see generateIcons).
	 */
	public static Icon getApplicationLogo(int size) {
		return new ImageIcon(new ScalableImage(getLogoImage("logo"), size, size));
	}

	/**
	 * Application logo with the name (resources/logo.full.png, or logo.full.dark.png with light text for dark themes, see
	 * generateIcons).
	 */
	public static Icon getApplicationFullLogo(int height, boolean dark) {
		BufferedImage logo = getLogoImage(dark ? "logo.full.dark" : "logo.full");
		return new ImageIcon(new ScalableImage(logo, height * logo.getWidth() / logo.getHeight(), height));
	}

	/**
	 * Original logo of a data source (resources/sources/{name}.svg or .png, and {name}.dark.svg or .dark.png for dark
	 * themes) fitted into the given box, or null if there is none.
	 */
	public static Icon getSourceLogo(String name, boolean dark, int width, int height) {
		for (String logo : dark ? List.of("sources/" + name + ".dark", "sources/" + name) : List.of("sources/" + name)) {
			if (ResourceManager.class.getResource("resources/" + logo + ".svg") != null) {
				FlatSVGIcon icon = new FlatSVGIcon(RESOURCE_FOLDER + logo + ".svg");
				double scale = Math.min((double) width / icon.getIconWidth(), (double) height / icon.getIconHeight());
				return icon.derive((int) Math.round(icon.getIconWidth() * scale), (int) Math.round(icon.getIconHeight() * scale));
			}
			if (getImageResource(logo) != null) {
				BufferedImage image = getLogoImage(logo);
				double scale = Math.min((double) width / image.getWidth(), (double) height / image.getHeight());
				return new ImageIcon(new ScalableImage(image, (int) Math.round(image.getWidth() * scale), (int) Math.round(image.getHeight() * scale)));
			}
		}
		return null;
	}

	private static final Map<String, BufferedImage> logos = new ConcurrentHashMap<String, BufferedImage>(2);

	private static BufferedImage getLogoImage(String name) {
		return logos.computeIfAbsent(name, n -> {
			try {
				return ImageIO.read(getImageResource(n));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	/**
	 * Image that is resampled for the number of pixels it is painted on, so it stays sharp at any display scale.
	 */
	private static class ScalableImage extends AbstractMultiResolutionImage {

		private static final int MAX_VARIANTS = 4;

		private final BufferedImage source;
		private final int width;
		private final int height;
		private final Map<Dimension, Image> variants = new ConcurrentHashMap<Dimension, Image>(4);

		public ScalableImage(BufferedImage source, int width, int height) {
			this.source = source;
			this.width = width;
			this.height = height;
		}

		@Override
		protected Image getBaseImage() {
			return getResolutionVariant(width, height);
		}

		@Override
		public Image getResolutionVariant(double destImageWidth, double destImageHeight) {
			Dimension size = new Dimension(Math.max(1, (int) Math.round(destImageWidth)), Math.max(1, (int) Math.round(destImageHeight)));

			// keep only a few variants (e.g. after moving the window between screens with different scale factors)
			if (variants.size() >= MAX_VARIANTS && !variants.containsKey(size)) {
				variants.clear();
			}
			return variants.computeIfAbsent(size, d -> resize(source, d.width, d.height));
		}

		@Override
		public List<Image> getResolutionVariants() {
			return List.copyOf(variants.values());
		}
	}

	private static BufferedImage resize(BufferedImage image, int width, int height) {
		// area averaging keeps small logos sharper than the progressive bicubic downscale
		if (width <= image.getWidth() && height <= image.getHeight()) {
			BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = scaled.createGraphics();
			g.drawImage(image.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING), 0, 0, null);
			g.dispose();
			return scaled;
		}
		return Scalr.resize(image, Method.ULTRA_QUALITY, Mode.FIT_EXACT, width, height);
	}

	private static final String RESOURCE_FOLDER = "dev/davamu/filebotpro/resources/";
	private static final String SVG_FOLDER = RESOURCE_FOLDER + "svg/";

	/**
	 * Scalable icon from resources/svg (rendered sharply on HiDPI screens).
	 */
	public static FlatSVGIcon getSvgIcon(String name, int size) {
		return new FlatSVGIcon(SVG_FOLDER + name + ".svg", size, size);
	}

	/**
	 * Monochrome scalable icon tinted with the given color (evaluated on every paint, so it follows theme changes).
	 */
	public static FlatSVGIcon getSvgIcon(String name, int size, Supplier<Color> color) {
		FlatSVGIcon icon = getSvgIcon(name, size);
		icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> {
			Color t = color.get();
			return new Color(t.getRed(), t.getGreen(), t.getBlue(), c.getAlpha() * t.getAlpha() / 255);
		}));
		return icon;
	}

	private static final Properties symbols = loadSymbols();

	private static Properties loadSymbols() {
		Properties properties = new Properties();
		try (InputStream in = ResourceManager.class.getResourceAsStream("resources/svg/icons.properties")) {
			if (in != null) {
				properties.load(in);
			}
		} catch (Exception e) {
			throw new IllegalStateException("Failed to load icon mapping", e);
		}
		return properties;
	}

	/**
	 * SVG replacement for a legacy PNG icon, tinted with the given color key or the foreground color of the component it is painted on.
	 */
	private static Icon getSymbolIcon(String name) {
		String value = symbols.getProperty(name);
		if (value == null) {
			return null;
		}

		String[] field = value.split(",", -1);
		String symbol = field[0].trim();
		String colorKey = field.length > 1 ? field[1].trim() : "";
		int size = field.length > 2 && !field[2].isBlank() ? Integer.parseInt(field[2].trim()) : 16;

		FlatSVGIcon icon = new FlatSVGIcon(SVG_FOLDER + "icons/" + symbol + ".svg", size, size);
		icon.setColorFilter(new FlatSVGIcon.ColorFilter((component, color) -> {
			Color tint = colorKey.isEmpty() ? getForeground(component) : UIManager.getColor(colorKey);
			if (tint == null) {
				tint = UIManager.getColor("Label.foreground");
			}
			return new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), color.getAlpha() * tint.getAlpha() / 255);
		}));
		return icon;
	}

	private static Color getForeground(Component c) {
		if (c instanceof JButton && ((JButton) c).isDefaultButton()) {
			return UIManager.getColor("Button.default.foreground");
		}
		return c != null ? c.getForeground() : UIManager.getColor("Label.foreground");
	}

	public static Icon getFlagIcon(String languageCode) {
		return getIcon("flags/" + languageCode);
	}

	private static URL[] getMultiResolutionImageResource(String name) {
		return Stream.of(name, name + "@2x").map(ResourceManager::getImageResource).filter(Objects::nonNull).toArray(URL[]::new);
	}

	private static URL getImageResource(String name) {
		return ResourceManager.class.getResource("resources/" + name + ".png");
	}

	private static Image getMultiResolutionImage(URL[] resource) {
		try {
			// Load multi-resolution images only if necessary
			if (PRIMARY_SCALE_FACTOR == 1) {
				return ImageIO.read(resource[0]);
			}

			List<BufferedImage> image = new ArrayList<BufferedImage>(resource.length);
			for (URL r : resource) {
				image.add(ImageIO.read(r));
			}

			// Windows 10: use down-scaled @2x image for non-integer scale factors 1.25 / 1.5 / 1.75
			if (PRIMARY_SCALE_FACTOR > 1 && PRIMARY_SCALE_FACTOR < 2 && image.size() > 1) {
				image.add(1, scale(PRIMARY_SCALE_FACTOR / 2, image.get(1)));
			} else if (PRIMARY_SCALE_FACTOR > 2) {
				image.add(scale(PRIMARY_SCALE_FACTOR / 2, image.get(1)));
			}

			return new BaseMultiResolutionImage(image.toArray(Image[]::new));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static Image getMultiResolutionImage(BufferedImage baseImage, double baseScale) {
		if (PRIMARY_SCALE_FACTOR == 1 && baseScale == 1) {
			return baseImage;
		}

		List<BufferedImage> image = new ArrayList<BufferedImage>(3);
		image.add(baseImage);

		// use down-scaled @2x image as @1x base image
		if (baseScale > 1) {
			image.add(0, scale(1 / baseScale, baseImage));
		}

		// Windows 10: use down-scaled @2x image for non-integer scale factors 1.25 / 1.5 / 1.75
		if (PRIMARY_SCALE_FACTOR > 1 && PRIMARY_SCALE_FACTOR < baseScale) {
			image.add(1, scale(PRIMARY_SCALE_FACTOR / baseScale, baseImage));
		} else if (PRIMARY_SCALE_FACTOR > baseScale) {
			image.add(scale(PRIMARY_SCALE_FACTOR / baseScale, baseImage));
		}

		return new BaseMultiResolutionImage(image.toArray(Image[]::new));
	}

	private static final double PRIMARY_SCALE_FACTOR = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform().getScaleX();

	private static BufferedImage scale(double scale, BufferedImage image) {
		int w = (int) (scale * image.getWidth());
		int h = (int) (scale * image.getHeight());
		return Scalr.resize(image, Method.ULTRA_QUALITY, Mode.FIT_TO_WIDTH, w, h, Scalr.OP_ANTIALIAS);
	}

}
