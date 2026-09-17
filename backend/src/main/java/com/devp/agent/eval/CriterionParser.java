package com.devp.agent.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.devp.agent.doc.MarkdownDocument;
import com.devp.agent.doc.MarkdownParser.MarkdownParseException;

/**
 * Reads the {@code success:} block from a task's front matter.
 *
 * <pre>
 * success:
 *   - type: tool-called
 *     value: currentTime
 *   - type: regex
 *     value: '\b14\b'
 *     description: integer quotient
 * </pre>
 *
 * <p>Errors surface when the file is loaded, not when a run is graded — a broken regular
 * expression would otherwise silently fail every run of the task.
 */
public final class CriterionParser {

	public static final String KEY = "success";

	private CriterionParser() {
	}

	public static List<Criterion> parse(MarkdownDocument document) {
		List<Criterion> criteria = new ArrayList<>();
		int index = 0;
		for (Map<?, ?> entry : document.objects(KEY)) {
			index++;
			String location = "%s[%d]".formatted(KEY, index);

			CriterionType type;
			try {
				type = CriterionType.of(text(entry, "type"));
			}
			catch (IllegalArgumentException ex) {
				throw new MarkdownParseException(document.source(), location + ": " + ex.getMessage());
			}

			String value = text(entry, "value");
			if (value == null) {
				throw new MarkdownParseException(document.source(), location + ": 'value' is missing");
			}
			if (type == CriterionType.REGEX || type == CriterionType.NOT_REGEX) {
				try {
					Pattern.compile(value);
				}
				catch (PatternSyntaxException ex) {
					throw new MarkdownParseException(document.source(),
							location + ": invalid regular expression — " + ex.getDescription());
				}
			}

			criteria.add(new Criterion(type, value, text(entry, "description")));
		}
		return List.copyOf(criteria);
	}

	private static String text(Map<?, ?> entry, String key) {
		Object value = entry.get(key);
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value).strip();
		return text.isEmpty() ? null : text;
	}
}
