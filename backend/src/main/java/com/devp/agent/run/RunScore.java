package com.devp.agent.run;

import java.time.Instant;

/**
 * Verdict of one grader on one run.
 *
 * <p>A run may carry several scores — deterministic check, judge agent, human. Each is its
 * own record and is never overwritten: only then can we later tell whether the graders
 * agreed.
 *
 * @param grader    who judged, e.g. {@code deterministic}
 * @param score     0.0 to 1.0 — share of satisfied criteria, or the grader's verdict
 * @param passed    overall verdict; for the deterministic grader: all criteria satisfied
 * @param rationale readable for humans, one line per criterion
 */
public record RunScore(
		String grader,
		double score,
		boolean passed,
		String rationale,
		Instant createdAt) {
}
