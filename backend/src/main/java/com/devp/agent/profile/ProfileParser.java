package com.devp.agent.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.devp.agent.doc.MarkdownDocument;
import com.devp.agent.doc.MarkdownParser;
import com.devp.agent.doc.MarkdownParser.MarkdownParseException;
import com.devp.agent.profile.Profile.StrategyConfig;

/**
 * Reads a strategy profile from Markdown with YAML front matter — the same format as SOULs
 * and tasks.
 *
 * <pre>
 * ---
 * id: reflexion
 * version: 1
 * strategies:
 *   - id: reflexion
 *     max-notes: 5
 *   - id: require-text
 *     text: Rest
 * ---
 * # Purpose
 * Prose for humans.
 * </pre>
 *
 * <p>Whether a strategy id exists is not checked here but in the repository against the
 * registered strategies — the parser knows the format, not the catalogue.
 */
public final class ProfileParser {

	public static final String KEY = "strategies";

	private ProfileParser() {
	}

	public static Profile parse(String content, String source) {
		MarkdownDocument document;
		try {
			document = MarkdownParser.parse(content, source);
			return new Profile(
					document.id(),
					document.text("name", document.id()),
					document.integer("version", 1),
					document.text("description"),
					strategies(document),
					document.sections(),
					source);
		}
		catch (MarkdownParseException ex) {
			throw new ProfileParseException(source, ex.reason());
		}
	}

	private static List<StrategyConfig> strategies(MarkdownDocument document) {
		List<StrategyConfig> strategies = new ArrayList<>();
		int index = 0;
		for (Map<?, ?> entry : document.objects(KEY)) {
			index++;
			Object rawId = entry.get("id");
			String id = rawId == null ? null : String.valueOf(rawId).strip();
			if (id == null || id.isEmpty()) {
				throw new MarkdownParseException(document.source(), "%s[%d]: 'id' is missing".formatted(KEY, index));
			}
			// Every other key is a parameter of the strategy; order kept for display.
			Map<String, Object> params = new LinkedHashMap<>();
			entry.forEach((k, v) -> {
				if (!"id".equals(String.valueOf(k))) {
					params.put(String.valueOf(k), v);
				}
			});
			strategies.add(new StrategyConfig(id, Collections.unmodifiableMap(params)));
		}
		return List.copyOf(strategies);
	}

	/** Profile file cannot be read — the message always names the source. */
	public static class ProfileParseException extends RuntimeException {
		public ProfileParseException(String source, String reason) {
			super("Profile '%s' could not be read: %s".formatted(source, reason));
		}
	}
}
