package com.devp.agent.eval;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Whether one criterion is satisfied by an answer and the tools a run called.
 *
 * <p>Extracted from the grader so a strategy can check the same thing before the answer is
 * handed in. Two implementations of the same rule would drift apart, and the first time they
 * disagreed the self-check would be teaching the agent something the grader does not reward.
 *
 * <p>The verdict stays the grader's: a strategy using this is preparing, not judging.
 */
public final class CriterionCheck {

	private CriterionCheck() {
	}

	/** @param tools names of the tools called in the run, in order */
	public static boolean satisfied(Criterion criterion, String answer, List<String> tools) {
		return switch (criterion.type()) {
			case CONTAINS -> answer != null && lower(answer).contains(lower(criterion.value()));
			case NOT_CONTAINS -> answer != null && !lower(answer).contains(lower(criterion.value()));
			case REGEX -> answer != null && Pattern.compile(criterion.value()).matcher(answer).find();
			case NOT_REGEX -> answer != null && !Pattern.compile(criterion.value()).matcher(answer).find();
			case TOOL_CALLED -> tools.contains(criterion.value());
			case TOOL_NOT_CALLED -> "*".equals(criterion.value()) ? tools.isEmpty() : !tools.contains(criterion.value());
		};
	}

	/**
	 * Can this criterion be judged from the answer alone? The tool criteria cannot: they need
	 * the trace, which is not available while the answer is still being written.
	 */
	public static boolean answerOnly(Criterion criterion) {
		return switch (criterion.type()) {
			case CONTAINS, NOT_CONTAINS, REGEX, NOT_REGEX -> true;
			case TOOL_CALLED, TOOL_NOT_CALLED -> false;
		};
	}

	private static String lower(String text) {
		return text.toLowerCase(Locale.ROOT);
	}
}
