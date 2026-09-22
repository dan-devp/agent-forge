package com.devp.forge.doc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Splits Markdown with YAML front matter into a {@link MarkdownDocument}.
 *
 * <pre>
 * ---
 * id: forge
 * version: 1
 * ---
 * # Identitaet
 * ...
 * </pre>
 *
 * <p>Knows only the file format, not the meaning of the fields — which fields are required
 * is decided by the entity (model, agent, task).
 */
public final class MarkdownParser {

	private static final String DELIMITER = "---";

	private MarkdownParser() {
	}

	/**
	 * @param source origin (path), also the fallback for a missing id
	 */
	public static MarkdownDocument parse(String content, String source) {
		if (content == null || content.isBlank()) {
			throw new MarkdownParseException(source, "file is empty");
		}

		Split split = splitFrontMatter(content, source);
		Map<String, Object> frontMatter = loadYaml(split.frontMatter(), source);

		Object rawId = frontMatter.get("id");
		String id = rawId == null ? null : String.valueOf(rawId).strip();
		if (id == null || id.isEmpty()) {
			throw new MarkdownParseException(source, "front matter needs an 'id'");
		}

		return new MarkdownDocument(id, frontMatter, sections(split.body()), split.body().strip(), source);
	}

	/**
	 * Front matter must start on the first line — otherwise it is impossible to tell
	 * whether a file has metadata or merely contains a horizontal rule.
	 */
	private static Split splitFrontMatter(String content, String source) {
		String normalized = content.replace("\r\n", "\n").stripLeading();
		if (!normalized.startsWith(DELIMITER)) {
			throw new MarkdownParseException(source, "no YAML front matter — file must start with '---'");
		}

		int start = normalized.indexOf('\n');
		if (start < 0) {
			throw new MarkdownParseException(source, "front matter is not closed");
		}

		int end = normalized.indexOf("\n" + DELIMITER, start);
		if (end < 0) {
			throw new MarkdownParseException(source, "front matter is not closed (second '---' missing)");
		}

		String frontMatter = normalized.substring(start + 1, end);
		int bodyStart = normalized.indexOf('\n', end + 1);
		String body = bodyStart < 0 ? "" : normalized.substring(bodyStart + 1);
		return new Split(frontMatter, body);
	}

	private static Map<String, Object> loadYaml(String frontMatter, String source) {
		try {
			Object loaded = new Yaml().load(frontMatter);
			if (loaded == null) {
				throw new MarkdownParseException(source, "front matter is empty");
			}
			if (!(loaded instanceof Map<?, ?> map)) {
				throw new MarkdownParseException(source, "front matter must be a YAML object");
			}
			Map<String, Object> result = new LinkedHashMap<>();
			map.forEach((k, v) -> result.put(String.valueOf(k), v));
			return Collections.unmodifiableMap(result);
		}
		catch (YAMLException ex) {
			throw new MarkdownParseException(source, "front matter is not valid YAML: " + ex.getMessage());
		}
	}

	/** Split the body into sections by {@code # Heading}, keeping the order. */
	private static Map<String, String> sections(String body) {
		Map<String, String> sections = new LinkedHashMap<>();
		String heading = null;
		StringBuilder buffer = new StringBuilder();

		for (String line : body.split("\n", -1)) {
			if (line.startsWith("# ")) {
				store(sections, heading, buffer);
				heading = line.substring(2).trim();
				buffer.setLength(0);
			}
			else {
				buffer.append(line).append('\n');
			}
		}
		store(sections, heading, buffer);
		// Map.copyOf loses the order — the section order is part of the definition.
		return Collections.unmodifiableMap(sections);
	}

	private static void store(Map<String, String> target, String heading, StringBuilder buffer) {
		if (heading == null) {
			return;
		}
		String text = buffer.toString().strip();
		if (!text.isEmpty()) {
			target.put(heading, text);
		}
	}

	private record Split(String frontMatter, String body) {
	}

	/** File cannot be read — the message always names the source. */
	public static class MarkdownParseException extends RuntimeException {

		private final String reason;

		public MarkdownParseException(String source, String reason) {
			super("'%s' could not be read: %s".formatted(source, reason));
			this.reason = reason;
		}

		/** The reason without the source — so an entity can embed it in its own message. */
		public String reason() {
			return reason;
		}
	}
}
