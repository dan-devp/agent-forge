package com.devp.forge.run;

/** Kind of a trace step. */
public enum StepType {

	/** One model round: either a tool request or the final answer. */
	MODEL,

	/** One executed tool call including arguments and result. */
	TOOL
}
