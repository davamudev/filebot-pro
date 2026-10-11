package dev.davamu.filebotpro.util;

import static java.nio.charset.StandardCharsets.*;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal binary property list (bplist00) codec for an array of strings, e.g. the macOS Finder tags in the
 * com.apple.metadata:_kMDItemUserTags extended attribute.
 *
 * https://opensource.apple.com/source/CF/CF-1153.18/CFBinaryPList.c
 */
public final class BinaryPropertyList {

	private static final byte[] MAGIC = "bplist00".getBytes(US_ASCII);

	public static byte[] encodeStringArray(List<String> values) {
		int count = values.size() + 1;
		if (count > 0xFF) {
			throw new IllegalArgumentException("Too many values: " + values.size());
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(MAGIC);

		int[] offsets = new int[count];

		// object 0: array with references to the string objects 1..n
		offsets[0] = out.size();
		writeMarker(out, 0xA0, values.size());
		for (int i = 1; i < count; i++) {
			out.write(i);
		}

		// object 1..n: strings
		for (int i = 1; i < count; i++) {
			offsets[i] = out.size();
			String s = values.get(i - 1);
			if (US_ASCII.newEncoder().canEncode(s)) {
				writeMarker(out, 0x50, s.length());
				out.writeBytes(s.getBytes(US_ASCII));
			} else {
				byte[] utf16 = s.getBytes(UTF_16BE);
				writeMarker(out, 0x60, utf16.length / 2);
				out.writeBytes(utf16);
			}
		}

		// offset table
		int offsetTableOffset = out.size();
		int offsetSize = offsetTableOffset > 0xFFFF ? 4 : offsetTableOffset > 0xFF ? 2 : 1;
		for (int offset : offsets) {
			writeInt(out, offset, offsetSize);
		}

		// trailer
		out.writeBytes(new byte[6]);
		out.write(offsetSize);
		out.write(1); // object reference size
		writeInt(out, count, 8);
		writeInt(out, 0, 8); // top object
		writeInt(out, offsetTableOffset, 8);

		return out.toByteArray();
	}

	public static List<String> decodeStringArray(byte[] bytes) {
		ByteBuffer data = ByteBuffer.wrap(bytes);
		for (int i = 0; i < MAGIC.length; i++) {
			if (data.get(i) != MAGIC[i]) {
				throw new IllegalArgumentException("Not a binary property list");
			}
		}

		int trailer = bytes.length - 32;
		int offsetSize = data.get(trailer + 6);
		int refSize = data.get(trailer + 7);
		int topObject = (int) data.getLong(trailer + 16);
		int offsetTableOffset = (int) data.getLong(trailer + 24);

		int array = readInt(data, offsetTableOffset + topObject * offsetSize, offsetSize);
		int marker = data.get(array) & 0xFF;
		if ((marker & 0xF0) != 0xA0) {
			throw new IllegalArgumentException("Not an array");
		}

		int[] position = { array + 1 };
		int length = readLength(data, marker, position);

		List<String> values = new ArrayList<String>(length);
		for (int i = 0; i < length; i++) {
			int ref = readInt(data, position[0] + i * refSize, refSize);
			int offset = readInt(data, offsetTableOffset + ref * offsetSize, offsetSize);
			int stringMarker = data.get(offset) & 0xFF;
			int[] stringPosition = { offset + 1 };
			int stringLength = readLength(data, stringMarker, stringPosition);

			switch (stringMarker & 0xF0) {
			case 0x50:
				values.add(new String(bytes, stringPosition[0], stringLength, US_ASCII));
				break;
			case 0x60:
				values.add(new String(bytes, stringPosition[0], stringLength * 2, UTF_16BE));
				break;
			case 0x70:
				values.add(new String(bytes, stringPosition[0], stringLength, UTF_8));
				break;
			default:
				break; // ignore other object types
			}
		}
		return values;
	}

	private static void writeMarker(ByteArrayOutputStream out, int type, int length) {
		if (length < 15) {
			out.write(type | length);
		} else {
			out.write(type | 0x0F);
			if (length <= 0xFF) {
				out.write(0x10);
				writeInt(out, length, 1);
			} else if (length <= 0xFFFF) {
				out.write(0x11);
				writeInt(out, length, 2);
			} else {
				out.write(0x12);
				writeInt(out, length, 4);
			}
		}
	}

	private static int readLength(ByteBuffer data, int marker, int[] position) {
		int length = marker & 0x0F;
		if (length == 0x0F) {
			int intMarker = data.get(position[0]++) & 0xFF;
			int size = 1 << (intMarker & 0x0F);
			length = readInt(data, position[0], size);
			position[0] += size;
		}
		return length;
	}

	private static void writeInt(ByteArrayOutputStream out, long value, int size) {
		for (int i = size - 1; i >= 0; i--) {
			out.write((int) (value >>> (8 * i)) & 0xFF);
		}
	}

	private static int readInt(ByteBuffer data, int position, int size) {
		long value = 0;
		for (int i = 0; i < size; i++) {
			value = (value << 8) | (data.get(position + i) & 0xFF);
		}
		return (int) value;
	}

	private BinaryPropertyList() {
		throw new UnsupportedOperationException();
	}

}
