package com.devp.forge.eval;

import java.util.List;

/**
 * One kind of check a task can ask for. Code, provided by the system; configured per task
 * through {@link #params()}.
 *
 * <p>Answers pass or fail, nothing in between. A verdict is never produced by the agent
 * that is being judged — an eval sees the finished run from the outside.
 */
public interface Eval {

	/** Stable key used in task files: {@code regex}, {@code tool-called}, {@code llm-judge}. */
	String type();

	/** Shown in the UI. German, because the person composing tasks reads it. */
	String label();

	String description();

	/** What a task may configure. The UI renders a form from this, the codec validates against it. */
	List<EvalParam> params();

	EvalResult evaluate(EvalConfig config, EvalSubject subject);

	/**
	 * All verdicts of this eval, one per part it checks. Most evals check one thing; a judge
	 * asked a list of questions answers each one, and each answer counts on its own. A
	 * verdict with a {@code subject} names its part.
	 */
	default List<EvalResult> evaluateAll(EvalConfig config, EvalSubject subject) {
		return List.of(evaluate(config, subject));
	}
}
