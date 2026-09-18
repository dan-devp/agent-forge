package com.devp.agent.training;

import java.time.Instant;
import java.util.UUID;

/**
 * One thing a training has learned, with provenance.
 *
 * @param strategyId which strategy wrote it
 * @param runId      the run it was learned from, {@code null} if written outside a run
 * @param kind       free label the strategy uses to sort its entries, e.g. {@code lesson}
 * @param content    what the strategy stores — usually text it will put into a later prompt
 */
public record TrainingMemory(
		long id,
		UUID trainingId,
		String strategyId,
		UUID runId,
		String kind,
		String content,
		Instant createdAt) {
}
