package com.devp.forge.training;

import java.time.Instant;
import java.util.UUID;

/**
 * One step of a session. Step 0 measures the starting version; every later step is one
 * proposal of the coach, written as a new agent version, measured, and decided.
 *
 * @param baseVersion  the version the coach improved on; {@code null} for the baseline
 * @param agentVersion the version measured here; {@code null} when the proposal was unusable
 * @param batchId      the measurement batch — its runs are the evidence behind the score
 * @param runTokens    tokens of the measurement: runs plus their judges
 * @param coachInput   exactly what the coach was sent, so a proposal can be traced to its cause
 * @param gate         what the gate said; {@code null} for the baseline and unusable proposals
 * @param decidedBy    {@code gate}, {@code user} or {@code baseline}
 */
public record Step(
		int seq,
		Integer baseVersion,
		Integer agentVersion,
		UUID batchId,
		Score score,
		Integer runTokens,
		String coachInput,
		String coachOutput,
		String rationale,
		String coachModel,
		Integer coachTokens,
		Long coachDurationMs,
		Decision gate,
		Decision decision,
		String decidedBy,
		String reason,
		Instant createdAt,
		Instant finishedAt) {
}
