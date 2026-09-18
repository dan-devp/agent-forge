package com.devp.agent.experiment;

/** State of an experiment. A failed arm does not fail the experiment — it is a result. */
public enum ExperimentStatus {

	/** Arms are still being trained, one at a time. Poll {@code GET /api/experiments/{id}}. */
	RUNNING,

	/** Every arm has a final state. */
	DONE,

	/** The experiment itself could not be executed — e.g. the task vanished between start and run. */
	FAILED
}
