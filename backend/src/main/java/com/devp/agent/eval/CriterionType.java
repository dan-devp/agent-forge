package com.devp.agent.eval;

import java.util.Arrays;

/**
 * Deterministically checkable success criteria of a task.
 *
 * <p>Deliberately model-free: only the answer text and the tool calls in the trace are
 * inspected. A judge agent becomes its own {@link Grader}, not another type here.
 */
public enum CriterionType {

	/** Answer contains the text (case-insensitive). */
	CONTAINS("contains"),

	/** Answer does not contain the text. Without an answer the criterion fails. */
	NOT_CONTAINS("not-contains"),

	/** Answer matches the regular expression (substring search). */
	REGEX("regex"),

	/** Answer does not match the regular expression. Without an answer it fails. */
	NOT_REGEX("not-regex"),

	/** The trace contains at least one call of this tool. */
	TOOL_CALLED("tool-called"),

	/** The trace contains no call of this tool; {@code *} means "no tool at all". */
	TOOL_NOT_CALLED("tool-not-called");

	private final String key;

	CriterionType(String key) {
		this.key = key;
	}

	/** Spelling used in the task file. */
	public String key() {
		return key;
	}

	public static CriterionType of(String key) {
		String wanted = key == null ? "" : key.strip();
		return Arrays.stream(values())
				.filter(type -> type.key.equalsIgnoreCase(wanted))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown criterion type '%s' — allowed: %s".formatted(key, allowed())));
	}

	private static String allowed() {
		return String.join(", ", Arrays.stream(values()).map(CriterionType::key).toList());
	}
}
