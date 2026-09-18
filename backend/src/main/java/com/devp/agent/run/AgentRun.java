package com.devp.agent.run;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One run of the agent: a goal, any number of model and tool steps, one result.
 *
 * <p>Carries the agent, SOUL, task and profile versions it ran under — a later evaluation
 * must be able to refer to the worker, the identity, the task revision and the active
 * strategies that produced the run. {@code taskId} is {@code null} when the goal was typed
 * freely; {@code trainingId} is {@code null} for a single run outside a training.
 *
 * <p>{@code temperature}, {@code systemPrompt} and {@code tools} record what the model
 * actually saw. Without them two runs are not comparable: a changed SOUL file, a
 * reconnected MCP server or a different provider setting would alter results unnoticed.
 *
 * <p>{@code scores} are the graders' verdicts; empty for a free-form goal, for a task without
 * criteria, and in the history listing.
 */
public record AgentRun(
		UUID id,
		String goal,
		String taskId,
		Integer taskVersion,
		String agentId,
		Integer agentVersion,
		String soulId,
		int soulVersion,
		String profileId,
		Integer profileVersion,
		UUID trainingId,
		String providerId,
		String model,
		Double temperature,
		String systemPrompt,
		List<String> tools,
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

	/** Without agent, profile, training, parameters and scores — for callers that do not know them (mostly tests). */
	public AgentRun(UUID id, String goal, String taskId, Integer taskVersion, String soulId, int soulVersion,
			String providerId, String model, RunStatus status, String answer, String error, int steps,
			Integer totalTokens, Long durationMs, Instant startedAt, Instant finishedAt, List<RunStep> trace) {
		this(id, goal, taskId, taskVersion, null, null, soulId, soulVersion, null, null, null, providerId, model,
				null, null, List.of(), status, answer, error, steps, totalTokens, durationMs, startedAt, finishedAt,
				trace, List.of());
	}

	public AgentRun withScores(List<RunScore> scores) {
		return new AgentRun(id, goal, taskId, taskVersion, agentId, agentVersion, soulId, soulVersion, profileId,
				profileVersion, trainingId, providerId, model, temperature, systemPrompt, tools, status, answer,
				error, steps, totalTokens, durationMs, startedAt, finishedAt, trace, scores);
	}

	/** The same run without its trace — for listings that must not load half the database. */
	public AgentRun withoutTrace() {
		return new AgentRun(id, goal, taskId, taskVersion, agentId, agentVersion, soulId, soulVersion, profileId,
				profileVersion, trainingId, providerId, model, temperature, systemPrompt, tools, status, answer,
				error, steps, totalTokens, durationMs, startedAt, finishedAt, List.of(), scores);
	}
}
