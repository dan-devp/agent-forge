package com.devp.agent.run;

/** Kind of a trace step. */
public enum StepType {

	/** One model round: either a tool request or the final answer. */
	MODEL,

	/** One executed tool call including arguments and result. */
	TOOL,

	/**
	 * A strategy rejected the answer before DONE and sent feedback. Its own type so the
	 * evaluation can later count how often a verifier saved a run and how often it only
	 * cost tokens.
	 */
	VERIFY
}
