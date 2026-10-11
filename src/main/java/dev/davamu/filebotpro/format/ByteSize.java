package dev.davamu.filebotpro.format;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * File size in bytes, e.g. {bytes} = 4815162342, {bytes.GB} = 4.8, {bytes.GiB} = 4.5, {bytes.format()} = 4.8 GB. Arithmetic
 * like {bytes / 1e9} works as with any number.
 */
public class ByteSize extends BigInteger {

	private static final long serialVersionUID = 1L;

	public ByteSize(long bytes) {
		super(Long.toString(bytes));
	}

	public BigDecimal getKB() {
		return scale(1e3);
	}

	public BigDecimal getMB() {
		return scale(1e6);
	}

	public BigDecimal getGB() {
		return scale(1e9);
	}

	public BigDecimal getTB() {
		return scale(1e12);
	}

	public BigDecimal getKiB() {
		return scale(1L << 10);
	}

	public BigDecimal getMiB() {
		return scale(1L << 20);
	}

	public BigDecimal getGiB() {
		return scale(1L << 30);
	}

	public BigDecimal getTiB() {
		return scale(1L << 40);
	}

	/**
	 * @return size with the largest fitting SI unit, e.g. 4.8 GB
	 */
	public String format() {
		String[] units = { "bytes", "KB", "MB", "GB", "TB" };
		double size = doubleValue();
		int unit = 0;
		while (size >= 1000 && unit < units.length - 1) {
			size /= 1000;
			unit++;
		}
		return unit == 0 ? longValue() + " " + units[unit] : BigDecimal.valueOf(size).setScale(1, RoundingMode.HALF_UP).toPlainString() + " " + units[unit];
	}

	private BigDecimal scale(double unit) {
		return new BigDecimal(this).divide(BigDecimal.valueOf(unit), 1, RoundingMode.HALF_UP);
	}

}
