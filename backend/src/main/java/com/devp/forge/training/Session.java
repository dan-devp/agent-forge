package com.devp.forge.training;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One execution of a training: the event to the training file's recipe.
 *
 * <p>Everything the session used is pinned when it starts — training version, coach version,
 * task split, n, budget — so a later edit of the file does not rewrite what this session was.
 * Agent versions are recorded per step.
 *
 * @param startVersion agent version the session started from (the baseline)
 * @param bestVersion  best version so far; the base of the next proposal
 * @param finalVersion agent version that is current when the session ended — the best one,
 *                     restored as a new version if a rejected candidate was left on top
 * @param totalTokens  runs + judges + coach, the cost axis of the whole session
 * @param note         why it ended, for people
 */
public record Session(
		UUID id,
		String trainingId,
		int trainingVersion,
		String agentId,
		String coachId,
		int coachVersion,
		Approval approval,
		int repetitions,
		int maxSteps,
		Integer maxTokens,
		List<String> train,
		List<String> holdout,
		SessionStatus status,
		int startVersion,
		Integer bestVersion,
		Integer finalVersion,
		int totalTokens,
		String error,
		String note,
		Instant startedAt,
		Instant finishedAt,
		List<Step> steps) {

	public Session {
		train = List.copyOf(train);
		holdout = List.copyOf(holdout);
		steps = steps == null ? List.of() : List.copyOf(steps);
	}

	public Session withSteps(List<Step> steps) {
		return new Session(id, trainingId, trainingVersion, agentId, coachId, coachVersion, approval, repetitions,
				maxSteps, maxTokens, train, holdout, status, startVersion, bestVersion, finalVersion, totalTokens, error,
				note, startedAt, finishedAt, steps);
	}
}
