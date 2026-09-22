package com.devp.agent.experiment;

/** State of an experiment. A failed training does not fail the experiment — it is a result. */
public enum ExperimentStatus {

	/** Trainings are still running, one at a time. Poll {@code GET /api/experiments/{id}}. */
	RUNNING,

	/** Every training has a final state. */
	DONE,

	/** Stopped by hand. The trainings that already ran keep their results — they are data, not waste. */
	CANCELLED,

	/** The experiment itself could not be executed — e.g. the task vanished between start and run. */
	FAILED
}
