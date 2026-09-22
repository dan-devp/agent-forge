package com.devp.forge.eval;

/**
 * Verdict of one eval on one run.
 *
 * <p>{@code model}, {@code tokens} and {@code durationMs} are set only by evals that call a
 * model. Those calls cost money like the run does, and a cost axis that leaves out the judge
 * lies.
 */
public record EvalResult(boolean passed, String rationale, String model, Integer tokens, Long durationMs) {

	public static EvalResult pass(String rationale) {
		return new EvalResult(true, rationale, null, null, null);
	}

	public static EvalResult fail(String rationale) {
		return new EvalResult(false, rationale, null, null, null);
	}
}
