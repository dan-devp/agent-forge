package com.devp.agent.agent;

import java.util.List;
import java.util.Locale;

import com.devp.agent.agent.Agent.ToolAccess;
import com.devp.agent.doc.MarkdownDocument;
import com.devp.agent.doc.MarkdownParser;
import com.devp.agent.doc.MarkdownParser.MarkdownParseException;

/**
 * Reads an agent from Markdown with YAML front matter — the same format as SOULs, tasks and
 * profiles.
 *
 * <pre>
 * ---
 * id: forge
 * name: Forge
 * version: 1
 * provider: local
 * soul: forge
 * tools: [current_time, divide]
 * ---
 * # Purpose
 * Prose for humans.
 * </pre>
 *
 * <p>{@code tools: all} means every registered tool, {@code tools: none} or an empty list
 * means none. A missing key means {@code all}: an agent file written before governance
 * existed must keep working, and the UI shows the difference.
 *
 * <p>Whether the named SOUL, provider or tool exists is not checked here — the parser knows
 * the format, the catalogue is checked where it is known.
 */
public final class AgentParser {

	public static final String TOOLS = "tools";

	private AgentParser() {
	}

	public static Agent parse(String content, String source) {
		try {
			MarkdownDocument document = MarkdownParser.parse(content, source);
			return new Agent(
					document.id(),
					document.text("name", document.id()),
					document.integer("version", 1),
					document.text("description"),
					document.text("provider"),
					document.text("soul"),
					tools(document),
					document.sections(),
					source);
		}
		catch (MarkdownParseException ex) {
			throw new AgentParseException(source, ex.reason());
		}
	}

	private static ToolAccess tools(MarkdownDocument document) {
		if (!document.frontMatter().containsKey(TOOLS)) {
			return ToolAccess.ALL;
		}
		List<String> names = document.strings(TOOLS);
		if (names.size() == 1) {
			String single = names.getFirst().toLowerCase(Locale.ROOT);
			if ("all".equals(single)) {
				return ToolAccess.ALL;
			}
			if ("none".equals(single)) {
				return ToolAccess.NONE;
			}
		}
		return ToolAccess.of(names);
	}

	/** Agent file cannot be read — the message always names the source. */
	public static class AgentParseException extends RuntimeException {

		public AgentParseException(String source, String reason) {
			super("Agent '%s' could not be read: %s".formatted(source, reason));
		}
	}
}
