package dev.davamu.filebotpro.web;

import static java.nio.charset.StandardCharsets.*;
import static dev.davamu.filebotpro.Logging.*;
import static dev.davamu.filebotpro.util.FileUtilities.*;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import dev.davamu.filebotpro.util.ByteBufferOutputStream;

public final class WebRequest {

	private static final boolean LOG_RESPONSE_CONTENT = Boolean.parseBoolean(System.getProperty("dev.davamu.filebotpro.web.WebRequest.log.response"));

	private static final String ENCODING_GZIP = "gzip";
	private static final String CHARSET_UTF8 = "UTF-8";

	public static Reader getReader(URLConnection connection) throws IOException {
		try {
			connection.addRequestProperty("Accept-Encoding", ENCODING_GZIP);
			connection.addRequestProperty("Accept-Charset", CHARSET_UTF8);
		} catch (IllegalStateException e) {
			debug.log(Level.WARNING, e, e::toString);
		}

		Charset charset = getCharset(connection.getContentType());
		String encoding = connection.getContentEncoding();

		InputStream inputStream = connection.getInputStream();

		if (ENCODING_GZIP.equalsIgnoreCase(encoding)) {
			inputStream = new GZIPInputStream(inputStream);
		}
		inputStream = limit(inputStream);

		return new InputStreamReader(inputStream, charset);
	}

	/** Connect and read timeouts, a stalled server must not block a worker thread forever */
	public static final int CONNECT_TIMEOUT_MILLIS = 15_000;
	public static final int READ_TIMEOUT_MILLIS = 60_000;

	/** Upper bound for a single response body held in memory */
	public static final long MAX_RESPONSE_BYTES = 256L * 1024 * 1024;

	public static URLConnection openConnection(URL url) throws IOException {
		URLConnection connection = url.openConnection();
		connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
		connection.setReadTimeout(READ_TIMEOUT_MILLIS);
		return connection;
	}

	public static class ResponseTooLargeException extends IOException {

		public ResponseTooLargeException(String message) {
			super(message);
		}
	}

	private static InputStream limit(InputStream in) {
		return new FilterInputStream(in) {

			private long count = 0;

			private int check(int n) throws IOException {
				if (n > 0 && (count += n) > MAX_RESPONSE_BYTES) {
					throw new ResponseTooLargeException("Response too large (limit: " + MAX_RESPONSE_BYTES + " bytes)");
				}
				return n;
			}

			@Override
			public int read() throws IOException {
				int b = super.read();
				if (b >= 0) {
					check(1);
				}
				return b;
			}

			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				return check(super.read(b, off, len));
			}
		};
	}

	public static Document getDocument(URL url) throws Exception {
		return getDocument(openConnection(url));
	}

	public static Document getDocument(URLConnection connection) throws Exception {
		return getDocument(new InputSource(getReader(connection)));
	}

	public static Document getDocument(String xml) throws Exception {
		if (xml.isEmpty()) {
			return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		}

		return getDocument(new InputSource(new StringReader(xml)));
	}

	public static Document getDocument(InputSource source) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setValidating(false);
		factory.setFeature("http://xml.org/sax/features/namespaces", false);
		factory.setFeature("http://xml.org/sax/features/validation", false);

		// remote and user supplied XML must never resolve external entities or DTDs (XXE)
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
		trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
		trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		try {
			factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		} catch (IllegalArgumentException e) {
			debug.log(Level.FINE, e, e::toString);
		}
		return factory.newDocumentBuilder().parse(source);
	}

	private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean value) {
		try {
			factory.setFeature(feature, value);
		} catch (ParserConfigurationException e) {
			debug.log(Level.FINE, e, e::toString);
		}
	}

	public static ByteBuffer fetch(URL resource) throws IOException {
		return fetch(resource, 0, null, null, null);
	}

	public static ByteBuffer fetchIfModified(URL resource, long ifModifiedSince) throws IOException {
		return fetch(resource, ifModifiedSince, null, null, null);
	}

	public static ByteBuffer fetch(URL url, long ifModifiedSince, Object etag, Map<String, String> requestParameters, Consumer<Map<String, List<String>>> responseParameters) throws IOException {
		URLConnection connection = openConnection(url);

		if (ifModifiedSince > 0) {
			connection.setIfModifiedSince(ifModifiedSince);
		} else if (etag != null) {
			// If-Modified-Since must not be set if If-None-Match is set and vice versa
			connection.addRequestProperty("If-None-Match", etag.toString());
		}

		try {
			connection.addRequestProperty("Accept-Encoding", ENCODING_GZIP);
			connection.addRequestProperty("Accept-Charset", CHARSET_UTF8);
		} catch (IllegalStateException e) {
			debug.log(Level.WARNING, e, e::toString);
		}

		if (requestParameters != null) {
			requestParameters.forEach(connection::addRequestProperty);
		}

		int contentLength = connection.getContentLength();
		String encoding = connection.getContentEncoding();

		InputStream inputStream = connection.getInputStream();
		if (ENCODING_GZIP.equalsIgnoreCase(encoding)) {
			inputStream = new GZIPInputStream(inputStream);
		}
		inputStream = limit(inputStream);

		// store response headers
		if (responseParameters != null) {
			responseParameters.accept(connection.getHeaderFields());
		}

		ByteBufferOutputStream buffer = new ByteBufferOutputStream(contentLength >= 0 ? contentLength : BUFFER_SIZE);
		try {
			// read all
			buffer.transferFully(inputStream);
		} catch (IOException e) {
			// if the content length is not known in advance an IOException (Premature EOF)
			// is always thrown after all the data has been read
			if (contentLength >= 0 || e instanceof ResponseTooLargeException) {
				throw e;
			}
		} finally {
			inputStream.close();
		}

		// no data, e.g. If-Modified-Since requests
		if (contentLength < 0 && buffer.getByteBuffer().remaining() == 0) {
			return null;
		}

		return buffer.getByteBuffer();
	}

	public static ByteBuffer post(URL url, Map<String, ?> parameters, Map<String, String> requestParameters) throws IOException {
		byte[] postData = encodeParameters(parameters, true).getBytes("UTF-8");
		if (requestParameters != null && ENCODING_GZIP.equals(requestParameters.get("Content-Encoding"))) {
			postData = gzip(postData);
		}
		return post(url, postData, "application/x-www-form-urlencoded", requestParameters);
	}

	public static ByteBuffer post(URL url, byte[] postData, String contentType, Map<String, String> requestParameters) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) openConnection(url);

		connection.addRequestProperty("Content-Length", String.valueOf(postData.length));
		connection.addRequestProperty("Content-Type", contentType);

		connection.setRequestMethod("POST");
		connection.setDoOutput(true);

		if (requestParameters != null) {
			for (Entry<String, String> parameter : requestParameters.entrySet()) {
				connection.addRequestProperty(parameter.getKey(), parameter.getValue());
			}
		}

		// write post data
		OutputStream out = connection.getOutputStream();
		out.write(postData);
		out.close();

		// read response
		int contentLength = connection.getContentLength();
		String encoding = connection.getContentEncoding();

		InputStream inputStream = connection.getInputStream();
		if (ENCODING_GZIP.equalsIgnoreCase(encoding)) {
			inputStream = new GZIPInputStream(inputStream);
		}
		inputStream = limit(inputStream);

		ByteBufferOutputStream buffer = new ByteBufferOutputStream(contentLength >= 0 ? contentLength : BUFFER_SIZE);
		try {
			// read all
			buffer.transferFully(inputStream);
		} catch (IOException e) {
			// if the content length is not known in advance an IOException (Premature EOF)
			// is always thrown after all the data has been read
			if (contentLength >= 0 || e instanceof ResponseTooLargeException) {
				throw e;
			}
		} finally {
			inputStream.close();
		}

		return buffer.getByteBuffer();
	}

	public static int head(URL url) throws IOException {
		HttpURLConnection c = (HttpURLConnection) openConnection(url);
		c.setRequestMethod("HEAD");
		return c.getResponseCode();
	}

	public static String encodeParameters(Map<String, ?> parameters, boolean unicode) {
		StringBuilder sb = new StringBuilder();

		for (Entry<String, ?> entry : parameters.entrySet()) {
			if (entry.getValue() != null) {
				if (sb.length() > 0) {
					sb.append("&");
				}
				sb.append(entry.getKey()).append("=").append(encode(entry.getValue().toString(), unicode));
			}
		}

		return sb.toString();
	}

	private static byte[] gzip(byte[] data) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
		try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
			gzip.write(data);
		}
		return out.toByteArray();
	}

	public static String encode(String string, boolean unicode) {
		try {
			return URLEncoder.encode(string, unicode ? "UTF-8" : "ISO-8859-1");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	public static Optional<String> getETag(Map<String, List<String>> responseHeaders) {
		List<String> header = responseHeaders.get("ETag");
		if (header != null && header.size() > 0) {
			// e.g. W/"ca0072135d8a475a716e6595f577ae8b"
			return Optional.of(header.get(0));
		}
		return Optional.empty();
	}

	private static Charset getCharset(String contentType) {
		if (contentType != null) {
			// e.g. Content-Type: text/html; charset=iso-8859-1
			Matcher matcher = Pattern.compile("charset=[\"]?([\\p{Graph}&&[^\"]]+)[\"]?").matcher(contentType);

			if (matcher.find()) {
				try {
					return Charset.forName(matcher.group(1));
				} catch (IllegalArgumentException e) {
					debug.warning("Illegal charset: " + contentType);
				}
			}

			// use http default encoding only for text/html
			if (contentType.equals("text/html")) {
				return ISO_8859_1;
			}
		}

		// use UTF-8 if we don't know any better
		return UTF_8;
	}

	public static String getXmlString(Document dom, boolean indent) throws TransformerException {
		Transformer tr = TransformerFactory.newInstance().newTransformer();
		tr.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		tr.setOutputProperty(OutputKeys.INDENT, indent ? "yes" : "no");

		// create string from dom
		StringWriter buffer = new StringWriter();
		tr.transform(new DOMSource(dom), new StreamResult(buffer));
		return buffer.toString();
	}

	public static void validateXml(String xml) throws SAXException, ParserConfigurationException, IOException {
		if (xml.isEmpty())
			return;

		SAXParserFactory sax = SAXParserFactory.newInstance();
		sax.setValidating(false);
		sax.setNamespaceAware(false);
		sax.setFeature("http://xml.org/sax/features/external-general-entities", false);
		sax.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		sax.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

		XMLReader reader = sax.newSAXParser().getXMLReader();

		// throw exception on error
		reader.setErrorHandler(new DefaultHandler());
		reader.parse(new InputSource(new StringReader(xml)));
	}

	/**
	 * Hide API keys, tokens and passwords in log messages (query parameters of request URLs).
	 */
	public static String redact(String text) {
		return text.replaceAll("(?i)\\b(api_?key|key|client|token|access_token|password|pwd|passwd|secret)=[^&\\s]+", "$1=***");
	}

	public static Supplier<String> log(URL url, long lastModified, Object etag) {
		return () -> {
			List<String> headers = new ArrayList<String>(2);
			if (etag != null) {
				headers.add("If-None-Match: " + etag);
			}
			if (lastModified > 0) {
				headers.add("If-Modified-Since: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastModified), ZoneOffset.UTC)));
			}
			return "Fetch resource: " + redact(String.valueOf(url)) + (headers.isEmpty() ? "" : " " + headers);
		};
	}

	public static Supplier<String> log(ByteBuffer data) {
		return () -> {
			if (data == null) {
				return "Received 0 bytes";
			}

			if (LOG_RESPONSE_CONTENT) {
				try {
					CharBuffer textContent = UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(data.duplicate());
					return String.format("Received %s%n%s%n", formatSize(data.remaining()), textContent);
				} catch (Exception e) {
					return String.format("Received %s%n[%s]%n", formatSize(data.remaining()), md5(data.duplicate()));
				}
			}

			return "Received " + formatSize(data.remaining());
		};

	}

	private WebRequest() {
		throw new UnsupportedOperationException();
	}

}
