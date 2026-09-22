package com.devp.forge.run;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One run: an agent on a task, any number of model and tool steps, one answer, verdicts.
 *
 * <p>Everything the run used is recorded by id <em>and version</em>, and what the model saw
 * (system prompt, input, temperature, offered tools) is written when the run is created,
 * before any call. A run whose recipe cannot be reconstructed is a number without a meaning.
 *
 * <p>{@code batchId} groups the runs one start produced; {@code null} for a single run.
 * {@code taskId} is {@code null} for an ad-hoc input; such a run has no evals.
 * {@code passed} is {@code null} when nothing was evaluated, otherwise all evals passed.
 */
public record Run(
		UUID id,
		UUID batchId,
		String taskId,
		Integer taskVersion,
		String agentId,
		int agentVersion,
		String modelId,
		int modelVersion,
		String modelName,
		double temperature,
		String systemPrompt,
		String input,
		List<String> tools,
		RunStatus status,
		String answer,
		String error,
		int steps,
		Integer totalTokens,
		Long durationMs,
		Boolean passed,
		Instant queuedAt,
		Instant startedAt,
		Instant finishedAt,
		List<RunStep> trace,
		List<RunEval> evals) {

	public Run withEvals(List<RunEval> evals) {
		Boolean passed = evals.isEmpty() ? null : evals.stream().allMatch(RunEval::passed);
		return new Run(id, batchId, taskId, taskVersion, agentId, agentVersion, modelId, modelVersion, modelName,
				temperature, systemPrompt, input, tools, status, answer, error, steps, totalTokens, durationMs, passed,
				queuedAt, startedAt, finishedAt, trace, evals);
	}

	/** Same run, listing shape: no trace, no evals, no long texts. */
	public Run summary() {
		return new Run(id, batchId, taskId, taskVersion, agentId, agentVersion, modelId, modelVersion, modelName,
				temperature, null, null, tools, status, answer, error, steps, totalTokens, durationMs, passed,
				queuedAt, startedAt, finishedAt, List.of(), List.of());
	}

	/** Finished shape: final status, answer, error, trace, totals. */
	public Run finished(RunStatus status, String answer, String error, String reportedModel, Integer totalTokens,
			Instant end, List<RunStep> trace) {
		return new Run(id, batchId, taskId, taskVersion, agentId, agentVersion, modelId, modelVersion,
				reportedModel == null ? modelName : reportedModel, temperature, systemPrompt, input, tools, status,
				answer, error, trace.size(), totalTokens, end.toEpochMilli() - startedAt.toEpochMilli(), null, queuedAt,
				startedAt, end, trace, List.of());
	}

	public Run withStatus(RunStatus status, Instant startedAt) {
		return new Run(id, batchId, taskId, taskVersion, agentId, agentVersion, modelId, modelVersion, modelName,
				temperature, systemPrompt, input, tools, status, answer, error, steps, totalTokens, durationMs, passed,
				queuedAt, startedAt, finishedAt, trace, evals);
	}
}
