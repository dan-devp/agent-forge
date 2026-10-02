package com.devp.forge.training;

/** State of a training session. Like a run, an aborted session is a result, not an error. */
public enum SessionStatus {

	/** Measuring or asking the coach. */
	RUNNING,

	/** A measured candidate waits for a human decision (approval: manual). */
	WAITING,

	/** Ran through: steps used up, or nothing left to learn. */
	DONE,

	/** Stopped by hand. */
	STOPPED,

	/** Token budget used up. */
	BUDGET_EXCEEDED,

	/** Coach or measurement failed, or the backend restarted mid-session. */
	FAILED;

	public boolean isOpen() {
		return this == RUNNING || this == WAITING;
	}
}
