package com.devp.agent.soul;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.devp.agent.doc.MarkdownDocument;
import com.devp.agent.doc.MarkdownParser;
import com.devp.agent.doc.MarkdownParser.MarkdownParseException;

/**
 * Reads a SOUL from Markdown with YAML front matter.
 *
 * <p>The file format comes from {@link MarkdownParser} and is shared with tasks (and later
 * skills). Only what a SOUL makes of it lives here — in particular the gates.
 *
 * <pre>
 * ---
 * id: forge
 * version: 1
 * gates:
 *   - id: evidence
 *     question: ...
 *     blocking: true
 * ---
 * # Identitaet
 * ...
 * </pre>
 */
public final class SoulParser {

	private SoulParser() {
	}

	/**
	 * @param content complete file content
	 * @param source  origin (path), also the fallback for a missing id
	 */
	public static Soul parse(String content, String source) {
		MarkdownDocument document;
		int version;
		try {
			document = MarkdownParser.parse(content, source);
			version = document.integer("version", 1);
		}
		catch (MarkdownParseException ex) {
			throw new SoulParseException(source, ex.reason());
		}

		return new Soul(
				document.id(),
				document.text("name", document.id()),
				document.text("role"),
				version,
				document.text("description"),
				gates(document),
				document.sections(),
				source);
	}

	/**
	 * Gates are the SOUL-specific part: mandatory questions plus whether a negative answer
	 * forbids delivering.
	 */
	private static List<SoulGate> gates(MarkdownDocument document) {
		List<Map<?, ?>> raw;
		try {
			raw = document.objects("gates");
		}
		catch (MarkdownParseException ex) {
			throw new SoulParseException(document.source(), ex.reason());
		}

		List<SoulGate> gates = new ArrayList<>();
		for (Map<?, ?> entry : raw) {
			String id = text(entry.get("id"));
			String question = text(entry.get("question"));
			if (id == null || id.isBlank() || question == null || question.isBlank()) {
				throw new SoulParseException(document.source(), "gate without 'id' or 'question': " + entry);
			}
			gates.add(new SoulGate(id, question, Boolean.TRUE.equals(entry.get("blocking"))));
		}
		return List.copyOf(gates);
	}

	private static String text(Object value) {
		return value == null ? null : String.valueOf(value).strip();
	}

	/** SOUL file cannot be read — the message always names the source. */
	public static class SoulParseException extends RuntimeException {
		public SoulParseException(String source, String reason) {
			super("SOUL '%s' could not be read: %s".formatted(source, reason));
		}
	}
}
