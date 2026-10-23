package dev.davamu.filebotpro;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "history")
public class History {

	@XmlElement(name = "sequence")
	private List<Sequence> sequences;

	public History() {
		this.sequences = new ArrayList<Sequence>();
	}

	public History(Collection<Sequence> sequences) {
		this.sequences = new ArrayList<Sequence>(sequences);
	}

	public static class Sequence {

		@XmlAttribute(name = "date", required = true)
		private Date date;

		// sequences of the same process (e.g. a single -rename or -script call) have the same session id (null for history of older versions)
		@XmlAttribute(name = "session")
		private String session;

		@XmlElement(name = "rename", required = true)
		private List<Element> elements;

		private Sequence() {
			// hide constructor
		}

		public Date date() {
			return date;
		}

		public String session() {
			return session;
		}

		public List<Element> elements() {
			if (elements == null)
				return emptyList();

			return unmodifiableList(elements);
		}

		void addAll(Collection<Element> elements) {
			this.elements.addAll(elements);
		}

		Sequence copy() {
			Sequence copy = new Sequence();
			copy.date = date;
			copy.session = session;
			copy.elements = new ArrayList<Element>(elements());
			return copy;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Sequence) {
				Sequence other = (Sequence) obj;
				return date.equals(other.date) && elements.equals(other.elements);
			}

			return false;
		}

		@Override
		public int hashCode() {
			return Objects.hash(elements, date);
		}
	}

	public static class Element {

		@XmlAttribute(name = "dir", required = true)
		private File dir;

		@XmlAttribute(name = "from", required = true)
		private String from;

		@XmlAttribute(name = "to", required = true)
		private String to;

		public Element() {
			// used by JAXB
		}

		public Element(String from, String to, File dir) {
			this.from = from;
			this.to = to;
			this.dir = dir;
		}

		public File dir() {
			return dir;
		}

		public String from() {
			return from;
		}

		public String to() {
			return to;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Element) {
				Element element = (Element) obj;
				return to.equals(element.to) && from.equals(element.from) && dir.getPath().equals(element.dir.getPath());
			}

			return false;
		}

		@Override
		public int hashCode() {
			return Objects.hash(to, from, dir);
		}
	}

	public List<Sequence> sequences() {
		return unmodifiableList(sequences);
	}

	public void add(Collection<Element> elements) {
		add(elements, null);
	}

	public Sequence add(Collection<Element> elements, String session) {
		Sequence sequence = new Sequence();
		sequence.date = new Date();
		sequence.session = session;
		sequence.elements = new ArrayList<Element>(elements);

		add(sequence);
		return sequence;
	}

	/**
	 * Sequences of the most recent operation, i.e. the last sequence and all other sequences of the same session
	 */
	public List<Sequence> getLastOperation() {
		if (sequences.isEmpty()) {
			return emptyList();
		}

		Sequence last = sequences.get(sequences.size() - 1);
		if (last.session == null) {
			return singletonList(last);
		}

		return sequences.stream().filter(s -> last.session.equals(s.session)).collect(toList());
	}

	/**
	 * Original and current path of each renamed file in the given sequences (most recent first)
	 */
	public static List<Map.Entry<File, File>> getRevertList(List<Sequence> sequences) {
		List<Map.Entry<File, File>> list = new ArrayList<Map.Entry<File, File>>();
		for (Sequence seq : sequences) {
			for (Element elem : seq.elements()) {
				File to = new File(elem.to());
				if (!to.isAbsolute()) {
					to = new File(elem.dir(), elem.to());
				}
				list.add(Map.entry(new File(elem.dir(), elem.from()), to));
			}
		}
		reverse(list);
		return list;
	}

	public void add(Sequence sequence) {
		this.sequences.add(sequence);
	}

	public void addAll(Collection<Sequence> sequences) {
		this.sequences.addAll(sequences);
	}

	/**
	 * Replace the most recent sequence equal to the given previous sequence
	 */
	public boolean replace(Sequence previous, Sequence sequence) {
		int i = sequences.lastIndexOf(previous);
		if (i < 0) {
			return false;
		}

		sequences.set(i, sequence);
		return true;
	}

	public void merge(History history) {
		for (Sequence sequence : history.sequences()) {
			if (!sequences.contains(sequence)) {
				add(sequence);
			}
		}
	}

	public int totalSize() {
		int i = 0;
		for (Sequence it : sequences()) {
			i += it.elements.size();
		}
		return i;
	}

	public void clear() {
		sequences.clear();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof History) {
			History other = (History) obj;
			return sequences.equals(other.sequences);
		}

		return false;
	}

	@Override
	public int hashCode() {
		return sequences.hashCode();
	}

	public Map<File, File> getRenameMap() {
		Map<File, File> map = new LinkedHashMap<File, File>();
		for (History.Sequence seq : sequences) {
			for (History.Element elem : seq.elements()) {
				File to = new File(elem.to());
				if (!to.isAbsolute()) {
					to = new File(elem.dir(), elem.to());
				}
				File from = new File(elem.dir(), elem.from());
				map.put(from, to);
			}
		}
		return map;
	}

	public Stream<File> getOriginalPath(File destination) {
		return sequences.stream().flatMap(s -> s.elements().stream()).filter(e -> {
			File to = new File(e.to());
			if (!to.isAbsolute()) {
				to = new File(e.dir(), e.to());
			}
			return destination.equals(to);
		}).map(e -> new File(e.dir(), e.from()));
	}

	private static JAXBContext context;

	private static synchronized JAXBContext getContext() throws JAXBException {
		// JAXBContext is thread-safe and expensive to create
		if (context == null) {
			context = JAXBContext.newInstance(History.class);
		}
		return context;
	}

	public static void exportHistory(History history, OutputStream output) throws IOException {
		try {
			Marshaller marshaller = getContext().createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			marshaller.marshal(history, output);
		} catch (JAXBException e) {
			throw new IOException("Failed to write history: " + e, e);
		}
	}

	public static History importHistory(InputStream stream) throws IOException {
		try {
			Unmarshaller unmarshaller = getContext().createUnmarshaller();
			History history = (History) unmarshaller.unmarshal(stream);
			return history != null ? history : new History();
		} catch (JAXBException e) {
			throw new IOException("Failed to read history: " + e, e);
		}
	}

}
