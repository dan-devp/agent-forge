package com.devp.agent.task;

import com.devp.agent.doc.MarkdownDocument;
import com.devp.agent.doc.MarkdownParser;
import com.devp.agent.doc.MarkdownParser.MarkdownParseException;
import com.devp.agent.eval.CriterionParser;

/**
 * Reads a task from Markdown with YAML front matter — the same format as SOULs.
 *
 * <pre>
 * ---
 * id: time-and-division
 * name: Zeit und Rechnung
 * version: 1
 * difficulty: 1
 * tags: [tools]
 * max-tool-calls: 4
 * success:
 *   - type: tool-called
 *     value: currentTime
 * ---
 * # Goal
 * Wie spaet ist es in UTC, und was ergibt 100 geteilt durch 7? Nutze die Tools.
 * </pre>
 */
public final class TaskParser {

	/** Section whose text is sent to the agent as the user message. */
	public static final String SECTION_GOAL = "Goal";

	private TaskParser() {
	}

	public static Task parse(String content, String source) {
		MarkdownDocument document;
		try {
			document = MarkdownParser.parse(content, source);

			String goal = document.section(SECTION_GOAL);
			if (goal == null || goal.isBlank()) {
				// Without a goal there is nothing to execute — that is a defect in the file,
				// not an empty run at runtime.
				throw new TaskParseException(source,
						"section '# %s' is missing or empty".formatted(SECTION_GOAL));
			}

			return new Task(
					document.id(),
					document.text("name", document.id()),
					document.integer("version", 1),
					document.text("description"),
					document.integer("difficulty", 1),
					document.strings("tags"),
					document.text("soul"),
					document.text("provider"),
					document.text("profile"),
					document.integer("max-tool-calls"),
					document.longValue("max-duration-ms"),
					CriterionParser.parse(document),
					goal,
					document.sections(),
					source);
		}
		catch (MarkdownParseException ex) {
			throw new TaskParseException(source, ex.reason());
		}
	}

	/** Task file cannot be read — the message always names the source. */
	public static class TaskParseException extends RuntimeException {
		public TaskParseException(String source, String reason) {
			super("Task '%s' could not be read: %s".formatted(source, reason));
		}
	}
}
