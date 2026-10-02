package com.devp.forge.run;

import java.time.Instant;

/**
 * The verdict of one configured eval on one run, or of one part of it. Append-only.
 *
 * @param index      position of the eval in the task, 1-based
 * @param type       eval type key
 * @param subject    what this verdict is about when the eval gives several (a judge's question); else {@code null}
 * @param model      the judge's model id, for evals that called one; else {@code null}
 * @param tokens     tokens the judge spent; else {@code null}
 * @param durationMs time the judge took; else {@code null}
 */
public record RunEval(
		int index,
		String type,
		String subject,
		boolean passed,
		String rationale,
		String model,
		Integer tokens,
		Long durationMs,
		Instant createdAt) {
}
