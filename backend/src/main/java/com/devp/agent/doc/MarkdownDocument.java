package com.devp.agent.doc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A parsed Markdown file with YAML front matter.
 *
 * <p>Shared format of all authored entities of the framework — SOUL, task and later skill.
 * Deliberately close to agentskills.io: machine-readable metadata on top, prose in the body.
 * The history thus lives in Git instead of a table.
 *
 * @param frontMatter front matter, in file order
 * @param sections    {@code # Heading} -&gt; text, in file order
 * @param source      origin of the definition, for provenance in logs and errors
 */
public record MarkdownDocument(String id, Map<String, Object> frontMatter, Map<String, String> sections,
		String source) {

	/** Front matter value as text, {@code null} if absent or blank. */
	public String text(String key) {
		Object value = frontMatter.get(key);
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value).strip();
		return text.isEmpty() ? null : text;
	}

	public String text(String key, String fallback) {
		String value = text(key);
		return value == null ? fallback : value;
	}

	/** Integer from the front matter, {@code null} if absent. */
	public Integer integer(String key) {
		Object value = frontMatter.get(key);
		if (value == null) {
			return null;
		}
		if (value instanceof Number number) {
			return number.intValue();
		}
		try {
			return Integer.parseInt(String.valueOf(value).trim());
		}
		catch (NumberFormatException ex) {
			throw new MarkdownParser.MarkdownParseException(source, key + " must be a number, was: " + value);
		}
	}

	public int integer(String key, int fallback) {
		Integer value = integer(key);
		return value == null ? fallback : value;
	}

	public Long longValue(String key) {
		Integer value = integer(key);
		return value == null ? null : value.longValue();
	}

	/** List of strings from the front matter; a single value counts as a one-element list. */
	public List<String> strings(String key) {
		Object value = frontMatter.get(key);
		if (value == null) {
			return List.of();
		}
		if (value instanceof List<?> list) {
			return list.stream().map(e -> String.valueOf(e).strip()).filter(e -> !e.isEmpty()).toList();
		}
		String single = String.valueOf(value).strip();
		return single.isEmpty() ? List.of() : List.of(single);
	}

	/** List of objects from the front matter, e.g. the gates of a SOUL. */
	public List<Map<?, ?>> objects(String key) {
		Object value = frontMatter.get(key);
		if (value == null) {
			return List.of();
		}
		if (!(value instanceof List<?> list)) {
			throw new MarkdownParser.MarkdownParseException(source, key + " must be a list");
		}
		List<Map<?, ?>> objects = new ArrayList<>();
		for (Object entry : list) {
			if (!(entry instanceof Map<?, ?> map)) {
				throw new MarkdownParser.MarkdownParseException(source,
						"every entry under '%s' must be an object — found: %s".formatted(key, entry));
			}
			objects.add(map);
		}
		return List.copyOf(objects);
	}

	/** Body section by heading, {@code null} if absent. */
	public String section(String heading) {
		return sections.get(heading);
	}
}
