package com.devp.agent.series;

import java.time.Instant;
import java.util.UUID;

/**
 * N repetitions of one task under identical settings.
 *
 * <p>Only a named task can be repeated: a free-form goal has no version and no criteria, so
 * nothing could be compared. SOUL, profile and provider are the values that actually
 * applied, resolved once at the start — a task default changed halfway through would
 * otherwise split the series in two.
 *
 * @param completed runs finished so far, in any final state
 */
public record Series(
		UUID id,
		String taskId,
		int taskVersion,
		String soulId,
		String profileId,
		String providerId,
		int repetitions,
		SeriesStatus status,
		int completed,
		String error,
		Instant createdAt,
		Instant finishedAt) {

	public Series withProgress(int completed) {
		return new Series(id, taskId, taskVersion, soulId, profileId, providerId, repetitions, status, completed,
				error, createdAt, finishedAt);
	}

	public Series finished(SeriesStatus status, String error, Instant finishedAt) {
		return new Series(id, taskId, taskVersion, soulId, profileId, providerId, repetitions, status, completed,
				error, createdAt, finishedAt);
	}
}
