package dev.davamu.filebotpro.format;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Bit rate in bits per second, e.g. {vbr} = 6123456, {vbr.kbps} = 6123, {vbr.Mbps} = 6.1, {vbr.format()} = 6.1 Mbps. Arithmetic
 * like {bitrate / 1000} works as with any number.
 */
public class BitRate extends BigInteger {

	private static final long serialVersionUID = 1L;

	public BitRate(long bps) {
		super(Long.toString(bps));
	}

	public long getKbps() {
		return Math.round(doubleValue() / 1e3);
	}

	public BigDecimal getMbps() {
		return new BigDecimal(this).divide(BigDecimal.valueOf(1_000_000), 1, RoundingMode.HALF_UP);
	}

	/**
	 * @return e.g. 6.1 Mbps or 640 kbps
	 */
	public String format() {
		return doubleValue() >= 1e6 ? getMbps().toPlainString() + " Mbps" : getKbps() + " kbps";
	}

}
