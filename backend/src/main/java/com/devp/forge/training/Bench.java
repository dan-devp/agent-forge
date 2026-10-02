package com.devp.forge.training;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Measures one agent version on the training's tasks: n runs per task, wait, count.
 *
 * <p>An interface so the engine can be tested without a model: the real bench queues a batch
 * and waits for its runs, a test bench answers from the prompt.
 */
public interface Bench {

	/**
	 * Blocks until every run is final, or until {@code stop} says so — then the rest of the
	 * batch is cancelled and {@link Measurement#stopped()} is true.
	 */
	Measurement measure(String agentId, int agentVersion, List<String> train, List<String> holdout, int repetitions,
			BooleanSupplier stop);

	/**
	 * @param failures failed runs of the <em>train</em> tasks, at most a few per task — the
	 *                 coach's evidence. Holdout runs are counted, never shown.
	 * @param tokens   runs plus their judges
	 */
	record Measurement(UUID batchId, Score score, List<TaskScore> tasks, List<FailedRun> failures, int tokens,
			boolean stopped) {
	}

	record TaskScore(String taskId, boolean holdout, int passed, int total) {
	}

	/**
	 * A failed train run as the coach reads it: what was asked, what the agent did with its
	 * tools, what it answered, and which verdicts said no.
	 */
	record FailedRun(String taskId, UUID runId, String status, String input, String answer, String error,
			List<String> toolCalls, List<String> failedVerdicts) {
	}
}
