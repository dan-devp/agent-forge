package com.devp.agent.run;

import java.time.Instant;

/**
 * One step of the trace. Append-only: written once, never changed.
 *
 * @param seq    sequence number starting at 1 within the run
 * @param name   tool name or model id
 * @param input  tool arguments as JSON, or the request to the model
 * @param output tool result or model answer
 */
public record RunStep(
		int seq,
		StepType type,
		String name,
		String input,
		String output,
		Integer tokens,
		Long durationMs,
		String error,
		Instant createdAt) {
}
