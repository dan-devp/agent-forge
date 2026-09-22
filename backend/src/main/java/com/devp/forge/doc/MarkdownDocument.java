package com.devp.forge.doc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A parsed Markdown file with YAML front matter.
 *
 * <p>Shared format of all authored entities: machine-readable metadata on top, prose in the
 * body. What the fields mean is decided by the entity, not here.
 *
 * @param frontMatter front matter, in file order
 * @param sections    {@code # Heading} -&gt; text, in file order
 * @param body        the whole body after the front matter, stripped — for entities whose
 *                    body is one text (a system prompt, a task input) rather than sections
 * @param source      origin of the definition, for provenance in logs and errors
 */
public record MarkdownDocument(String id, Map<String, Object> frontMatter, Map<String, String> sections,
		String body, String source) {

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

	/** Boolean from the front matter, {@code fallback} if absent. */
	public boolean bool(String key, boolean fallback) {
		Object value = frontMatter.get(key);
		if (value == null) {
			return fallback;
		}
		if (value instanceof Boolean b) {
			return b;
		}
		return Boolean.parseBoolean(String.valueOf(value).trim());
	}

	/** Double from the front matter, {@code null} if absent. */
	public Double decimal(String key) {
		Object value = frontMatter.get(key);
		if (value == null) {
			return null;
		}
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		try {
			return Double.parseDouble(String.valueOf(value).trim());
		}
		catch (NumberFormatException ex) {
			throw new MarkdownParser.MarkdownParseException(source, key + " must be a number, was: " + value);
		}
	}

	/** Body section by heading, {@code null} if absent. */
	public String section(String heading) {
		return sections.get(heading);
	}
}
