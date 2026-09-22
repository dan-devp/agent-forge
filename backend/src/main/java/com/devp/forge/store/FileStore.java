package com.devp.forge.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.devp.forge.doc.MarkdownDocument;
import com.devp.forge.doc.MarkdownParser;
import com.devp.forge.doc.MarkdownParser.MarkdownParseException;
import com.devp.forge.doc.MarkdownWriter;
import com.devp.forge.store.StoreExceptions.EntityConflictException;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;
import com.devp.forge.store.StoreExceptions.UnknownEntityException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Versioned Markdown files for one kind of entity.
 *
 * <pre>
 * files/&lt;kind&gt;/&lt;id&gt;/v1.md
 * files/&lt;kind&gt;/&lt;id&gt;/v2.md
 * files/&lt;kind&gt;/_archive/&lt;id&gt;-20260922-141000/...
 * </pre>
 *
 * <p>"Current" is the highest version. Saving writes the next file and touches no other;
 * deleting moves the whole directory to {@code _archive}, because runs keep pointing at
 * id and version, and a trace whose recipe is gone is only half a trace.
 *
 * <p>Everything is held in memory after a scan and kept in step with each write. The
 * filesystem is the source of truth; {@link #reload()} rereads it after edits by hand.
 */
public class FileStore<T extends Versioned> {

	private static final Logger log = LoggerFactory.getLogger(FileStore.class);

	private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
	private static final Pattern VERSION_FILE = Pattern.compile("v(\\d+)\\.md");
	private static final String ARCHIVE = "_archive";

	private final Path root;
	private final EntityCodec<T> codec;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	/** id -> version -> entity. Both maps sorted so listings are stable. */
	private final TreeMap<String, TreeMap<Integer, T>> entities = new TreeMap<>();

	/**
	 * @param filesDir the {@code files/} directory; the kind's subdirectory is created
	 * @param seed     copy {@code classpath:seed/<kind>/*.md} into an empty directory
	 */
	public FileStore(Path filesDir, EntityCodec<T> codec, boolean seed) {
		this.root = filesDir.resolve(codec.kind());
		this.codec = codec;
		try {
			Files.createDirectories(root);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot create " + root, ex);
		}
		if (seed) {
			seedIfEmpty();
		}
		reload();
	}

	public String kind() {
		return codec.kind();
	}

	public Path root() {
		return root;
	}

	// --- Reading ---------------------------------------------------------------------------

	/** Current version of every entity. */
	public synchronized List<T> list() {
		return entities.values().stream().map(TreeMap::lastEntry).map(Map.Entry::getValue).toList();
	}

	/** Current version, or {@link UnknownEntityException}. */
	public synchronized T get(String id) {
		return find(id).orElseThrow(() -> new UnknownEntityException(codec.kind(), id));
	}

	public synchronized Optional<T> find(String id) {
		TreeMap<Integer, T> versions = entities.get(id);
		return versions == null ? Optional.empty() : Optional.of(versions.lastEntry().getValue());
	}

	public synchronized T get(String id, int version) {
		TreeMap<Integer, T> versions = entities.get(id);
		if (versions == null) {
			throw new UnknownEntityException(codec.kind(), id);
		}
		T entity = versions.get(version);
		if (entity == null) {
			throw new UnknownEntityException(codec.kind(), id, version);
		}
		return entity;
	}

	/** All versions of one entity, oldest first. */
	public synchronized List<T> versions(String id) {
		TreeMap<Integer, T> versions = entities.get(id);
		if (versions == null) {
			throw new UnknownEntityException(codec.kind(), id);
		}
		return List.copyOf(versions.values());
	}

	public synchronized boolean exists(String id) {
		return entities.containsKey(id);
	}

	/** The file behind one version — for the UI's "show source" and for provenance. */
	public String source(String id, int version) {
		get(id, version);
		return read(file(id, version));
	}

	// --- Writing ---------------------------------------------------------------------------

	/** Writes version 1. The id must be free. */
	public synchronized T create(T draft) {
		validateId(draft.id());
		if (entities.containsKey(draft.id())) {
			throw new EntityConflictException(
					"A %s with id '%s' already exists — save it instead.".formatted(singular(), draft.id()));
		}
		return write(codec.withVersion(draft, 1));
	}

	/** Writes the next version. The id must exist. */
	public synchronized T update(T draft) {
		TreeMap<Integer, T> versions = entities.get(draft.id());
		if (versions == null) {
			throw new UnknownEntityException(codec.kind(), draft.id());
		}
		return write(codec.withVersion(draft, versions.lastKey() + 1));
	}

	/**
	 * Moves the entity's directory to {@code _archive}. Nothing is lost; the id becomes free.
	 * Runs that recorded it keep their id and version, and the archived file still says
	 * what they were.
	 */
	public synchronized void archive(String id) {
		if (!entities.containsKey(id)) {
			throw new UnknownEntityException(codec.kind(), id);
		}
		Path from = root.resolve(id);
		String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		Path to = root.resolve(ARCHIVE).resolve(id + "-" + stamp);
		// Same id archived twice within a second (tests do that): number the second one.
		for (int n = 2; Files.exists(to); n++) {
			to = root.resolve(ARCHIVE).resolve(id + "-" + stamp + "-" + n);
		}
		try {
			Files.createDirectories(to.getParent());
			Files.move(from, to);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot archive %s '%s'".formatted(singular(), id), ex);
		}
		entities.remove(id);
		log.info("{} '{}' archived to {}", singular(), id, to);
		notifyListeners();
	}

	/** Called after every write and archive; repositories that cache derived objects hook in here. */
	public void onChange(Runnable listener) {
		listeners.add(listener);
	}

	// --- Scanning --------------------------------------------------------------------------

	/** Rereads every file. Broken files are logged and skipped, not fatal — a typo in one must not hide the rest. */
	public synchronized void reload() {
		entities.clear();
		try (Stream<Path> dirs = Files.list(root)) {
			dirs.filter(Files::isDirectory)
					.filter(dir -> !dir.getFileName().toString().equals(ARCHIVE))
					.sorted()
					.forEach(this::scanEntity);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read " + root, ex);
		}
		log.info("{} {} loaded from {}", entities.size(), codec.kind(), root);
		notifyListeners();
	}

	private void scanEntity(Path dir) {
		String id = dir.getFileName().toString();
		try (Stream<Path> files = Files.list(dir)) {
			files.filter(Files::isRegularFile).forEach(file -> {
				var match = VERSION_FILE.matcher(file.getFileName().toString());
				if (!match.matches()) {
					return;
				}
				int version = Integer.parseInt(match.group(1));
				try {
					T entity = decode(read(file), file.toString());
					if (!entity.id().equals(id)) {
						log.warn("{} skipped: front matter id '{}' differs from directory '{}'", file, entity.id(), id);
						return;
					}
					if (entity.version() != version) {
						log.warn("{} skipped: front matter version {} differs from file name", file, entity.version());
						return;
					}
					entities.computeIfAbsent(id, k -> new TreeMap<>()).put(version, entity);
				}
				catch (RuntimeException ex) {
					log.warn("{} skipped: {}", file, ex.getMessage());
				}
			});
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read " + dir, ex);
		}
	}

	private T decode(String content, String source) {
		try {
			MarkdownDocument document = MarkdownParser.parse(content, source);
			return codec.decode(document);
		}
		catch (MarkdownParseException ex) {
			throw new EntityInvalidException(ex.reason());
		}
	}

	private void seedIfEmpty() {
		try (Stream<Path> existing = Files.list(root)) {
			if (existing.findAny().isPresent()) {
				return;
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read " + root, ex);
		}
		try {
			Resource[] seeds = new PathMatchingResourcePatternResolver()
					.getResources("classpath*:seed/" + codec.kind() + "/*.md");
			for (Resource seed : seeds) {
				String content = seed.getContentAsString(StandardCharsets.UTF_8);
				T entity = decode(content, seed.getDescription());
				Path file = file(entity.id(), entity.version());
				Files.createDirectories(file.getParent());
				Files.writeString(file, content, StandardCharsets.UTF_8);
				log.info("Seeded {} '{}' v{}", singular(), entity.id(), entity.version());
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot seed " + root, ex);
		}
	}

	// --- Helpers ---------------------------------------------------------------------------

	private T write(T entity) {
		// Round-trip before touching the disk: what we write must be what we can read.
		String content = MarkdownWriter.write(codec.frontMatter(entity), codec.body(entity));
		T reread = decode(content, "%s/%s v%d".formatted(codec.kind(), entity.id(), entity.version()));

		Path file = file(entity.id(), entity.version());
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot write " + file, ex);
		}
		entities.computeIfAbsent(entity.id(), k -> new TreeMap<>()).put(entity.version(), reread);
		log.info("{} '{}' v{} written", singular(), entity.id(), entity.version());
		notifyListeners();
		return reread;
	}

	private Path file(String id, int version) {
		return root.resolve(id).resolve("v" + version + ".md");
	}

	private static String read(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read " + file, ex);
		}
	}

	private void notifyListeners() {
		listeners.forEach(Runnable::run);
	}

	private void validateId(String id) {
		if (id == null || !ID.matcher(id).matches()) {
			throw new EntityInvalidException(
					"id must be lowercase letters, digits and hyphens, starting with a letter or digit: '" + id + "'");
		}
	}

	private String singular() {
		String kind = codec.kind();
		return kind.endsWith("s") ? kind.substring(0, kind.length() - 1) : kind;
	}

	/** Shared by the codecs: reject a blank required field with a message that names it. */
	public static String required(MarkdownDocument document, String key) {
		String value = document.text(key);
		if (value == null) {
			throw new EntityInvalidException("'%s' is required".formatted(key));
		}
		return value;
	}

	public static void requireId(String id) {
		if (id == null || !ID.matcher(id).matches()) {
			throw new EntityInvalidException(
					"id must be lowercase letters, digits and hyphens, starting with a letter or digit: '" + id + "'");
		}
	}
}
