package com.devp.forge.run;

/** State of a run. An aborted run is a result, not an error. */
public enum RunStatus {

	/** Created, waiting for its turn on the run executor. */
	QUEUED,

	/** Executing — in the database after a crash it hints at exactly that. */
	RUNNING,

	/** The model answered without requesting further tools. */
	DONE,

	/** Budget exhausted (tool calls or time). The partial trace is kept. */
	BUDGET_EXCEEDED,

	/** Call failed — endpoint down, tool broken, configuration wrong. */
	FAILED,

	/** Withdrawn while still queued. Never started, nothing paid. */
	CANCELLED;

	public boolean isFinal() {
		return this != QUEUED && this != RUNNING;
	}
}
