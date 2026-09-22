package com.devp.forge.eval;

import java.util.List;

/**
 * What an eval gets to see: the finished run from the outside.
 *
 * @param input       the task input the agent was given
 * @param answer      the final answer, {@code null} when the run produced none
 * @param status      final run status as text ({@code DONE}, {@code BUDGET_EXCEEDED}, ...)
 * @param calledTools names of the tools called, in order, repeats included
 */
public record EvalSubject(String input, String answer, String status, List<String> calledTools) {

	public boolean hasAnswer() {
		return answer != null && !answer.isBlank();
	}
}
