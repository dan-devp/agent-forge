package com.devp.agent.run;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One run of the agent: a goal, any number of model and tool steps, one result.
 *
 * <p>Carries the SOUL and task versions it ran under — a later evaluation must be able to
 * refer to the identity and the task revision that produced the run. {@code taskId} is
 * {@code null} when the goal was typed freely.
 *
 * <p>{@code scores} are the graders' verdicts; empty for a free-form goal, for a task without
 * criteria, and in the history listing.
 */
public record AgentRun(
		UUID id,
		String goal,
		String taskId,
		Integer taskVersion,
		String soulId,
		int soulVersion,
		String providerId,
		String model,
		RunStatus status,
		String answer,
		String error,
		int steps,
		Integer totalTokens,
		Long durationMs,
		Instant startedAt,
		Instant finishedAt,
		List<RunStep> trace,
		List<RunScore> scores) {

	/** Without scores — for starting a run and for callers that do not know any yet. */
	public AgentRun(UUID id, String goal, String taskId, Integer taskVersion, String soulId, int soulVersion,
			String providerId, String model, RunStatus status, String answer, String error, int steps,
			Integer totalTokens, Long durationMs, Instant startedAt, Instant finishedAt, List<RunStep> trace) {
		this(id, goal, taskId, taskVersion, soulId, soulVersion, providerId, model, status, answer, error, steps,
				totalTokens, durationMs, startedAt, finishedAt, trace, List.of());
	}

	public AgentRun withScores(List<RunScore> scores) {
		return new AgentRun(id, goal, taskId, taskVersion, soulId, soulVersion, providerId, model, status, answer,
				error, steps, totalTokens, durationMs, startedAt, finishedAt, trace, scores);
	}
}
