package com.devp.forge.run;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One run: an agent on a task, any number of model and tool steps, one answer, verdicts.
 *
 * <p>Everything the run used is recorded by id <em>and version</em>, and what the model saw
 * (system prompt, temperature, offered tools) is written before the first call. A run whose
 * recipe cannot be reconstructed is a number without a meaning.
 *
 * <p>{@code taskId} is {@code null} for an ad-hoc input; such a run has no evals.
 * {@code passed} is {@code null} when nothing was evaluated, otherwise all evals passed.
 */
public record Run(
		UUID id,
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
		Instant startedAt,
		Instant finishedAt,
		List<RunStep> trace,
		List<RunEval> evals) {

	public Run withEvals(List<RunEval> evals) {
		Boolean passed = evals.isEmpty() ? null : evals.stream().allMatch(RunEval::passed);
		return new Run(id, taskId, taskVersion, agentId, agentVersion, modelId, modelVersion, modelName, temperature,
				systemPrompt, input, tools, status, answer, error, steps, totalTokens, durationMs, passed, startedAt,
				finishedAt, trace, evals);
	}

	/** Same run, listing shape: no trace, no evals. */
	public Run summary() {
		return new Run(id, taskId, taskVersion, agentId, agentVersion, modelId, modelVersion, modelName, temperature,
				null, null, tools, status, answer, error, steps, totalTokens, durationMs, passed, startedAt,
				finishedAt, List.of(), List.of());
	}
}
