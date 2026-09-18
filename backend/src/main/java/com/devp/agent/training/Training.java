package com.devp.agent.training;

import java.time.Instant;
import java.util.UUID;

/**
 * One strategy profile working on one task N times, in order.
 *
 * <p>The runs of a training are not independent repetitions: strategies may carry what they
 * learned from one run into the next through the training's memory. A training with the
 * empty {@code baseline} profile is the reference — its curve should stay flat; if it rises,
 * that is model variance, not learning.
 *
 * <p>Only a named task can be trained on: a free-form goal has no version and no criteria,
 * so nothing could be measured. SOUL, profile and provider are the values that actually
 * applied, resolved once at the start — a task default changed halfway through would
 * otherwise split the training in two.
 *
 * @param completed runs finished so far, in any final state
 */
public record Training(
		UUID id,
		String taskId,
		int taskVersion,
		String soulId,
		String profileId,
		String providerId,
		int repetitions,
		TrainingStatus status,
		int completed,
		String error,
		Instant createdAt,
		Instant finishedAt) {

	public Training withProgress(int completed) {
		return new Training(id, taskId, taskVersion, soulId, profileId, providerId, repetitions, status, completed,
				error, createdAt, finishedAt);
	}

	public Training finished(TrainingStatus status, String error, Instant finishedAt) {
		return new Training(id, taskId, taskVersion, soulId, profileId, providerId, repetitions, status, completed,
				error, createdAt, finishedAt);
	}
}
