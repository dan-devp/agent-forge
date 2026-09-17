package com.devp.agent.eval;

/**
 * One success criterion of a task.
 *
 * <p>The rubric is written by a human in the task file, never by the agent — it is the
 * external signal without which any reflection loop just agrees with itself.
 *
 * @param value       text, regular expression or tool name, depending on the type
 * @param description optional, for humans — shown in the rationale of a score
 */
public record Criterion(CriterionType type, String value, String description) {

	/** Short form for rationales: {@code contains "Rest 2"}. */
	public String display() {
		String core = type.key() + " \"" + value + "\"";
		return description == null ? core : core + " (" + description + ")";
	}
}
