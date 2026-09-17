package com.devp.agent.series;

/** State of a series. Individual runs keep their own status; a failed run does not fail the series. */
public enum SeriesStatus {

	/** Runs are still being executed. Poll {@code GET /api/series/{id}}. */
	RUNNING,

	/** All repetitions have a final state. */
	DONE,

	/** The series itself could not be executed — e.g. the task vanished between start and run. */
	FAILED
}
