package dev.davamu.filebotpro.format;

import java.math.BigDecimal;

/**
 * Frame rate, e.g. {fps} = 23.976, {fps.round()} = 24, {fps.format()} = 23.976 fps. Arithmetic works as with any number.
 */
public class FrameRate extends BigDecimal {

	private static final long serialVersionUID = 1L;

	public FrameRate(String fps) {
		super(fps);
	}

	/**
	 * @return e.g. 23.976 fps
	 */
	public String format() {
		return stripTrailingZeros().toPlainString() + " fps";
	}

}
