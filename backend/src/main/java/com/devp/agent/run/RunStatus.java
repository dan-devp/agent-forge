package com.devp.agent.run;

/** Final state of a run. An aborted run is a result, not an error. */
public enum RunStatus {

	/** Still running — only during execution; in the database it hints at a crash. */
	RUNNING,

	/** The model answered without requesting further tools. */
	DONE,

	/** Budget exhausted (steps, tool calls or time). The partial result is kept. */
	BUDGET_EXCEEDED,

	/** Call failed — provider down, tool broken, configuration wrong. */
	FAILED
}
