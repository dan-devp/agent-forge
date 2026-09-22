package com.devp.forge.task;

import java.util.List;

import com.devp.forge.eval.EvalConfig;
import com.devp.forge.store.Versioned;

/**
 * What is asked and how success is judged.
 *
 * <p>The input is the file's body and becomes the user message of the run. The evals are
 * written by a human and never by the agent — they are the external signal.
 *
 * @param maxToolCalls per-task budget, or {@code null} for the configured default
 */
public record Task(
		String id,
		int version,
		String name,
		String description,
		Integer maxToolCalls,
		List<EvalConfig> evals,
		String input) implements Versioned {

	public Task {
		evals = List.copyOf(evals);
	}
}
