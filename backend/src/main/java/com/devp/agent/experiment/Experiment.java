package com.devp.agent.experiment;

import java.time.Instant;
import java.util.UUID;

/**
 * One agent, one task, several strategy profiles — each trained N times, one after another.
 *
 * <p>The bracket above the trainings, and the smallest unit that actually measures anything:
 * a single training says how one profile did, only the comparison with the baseline says
 * whether it did better. The baseline is therefore part of the experiment rather than
 * something to remember to start by hand.
 *
 * <p>Everything that must stay equal between the arms is fixed here: agent, task revision,
 * provider and the number of repetitions. What differs is exactly one thing — the profile.
 *
 * @param name a label for humans; the arms are identified by their profile
 */
public record Experiment(
		UUID id,
		String name,
		String taskId,
		int taskVersion,
		String agentId,
		Integer agentVersion,
		String providerId,
		int repetitions,
		ExperimentStatus status,
		String error,
		Instant createdAt,
		Instant finishedAt) {

	public Experiment finished(ExperimentStatus status, String error, Instant finishedAt) {
		return new Experiment(id, name, taskId, taskVersion, agentId, agentVersion, providerId, repetitions, status,
				error, createdAt, finishedAt);
	}
}
