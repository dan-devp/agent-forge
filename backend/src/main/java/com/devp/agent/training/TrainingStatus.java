package com.devp.agent.training;

/** State of a training. Individual runs keep their own status; a failed run does not fail the training. */
public enum TrainingStatus {

	/** An arm of an experiment that is waiting for the arms before it. Nothing has run yet. */
	QUEUED,

	/** Runs are still being executed. Poll {@code GET /api/trainings/{id}}. */
	RUNNING,

	/** All repetitions have a final state. */
	DONE,

	/** The training itself could not be executed — e.g. the task vanished between start and run. */
	FAILED
}
