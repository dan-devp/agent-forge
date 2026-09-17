package com.devp.agent.task;

import java.util.List;
import java.util.Map;

import com.devp.agent.eval.Criterion;

/**
 * A reusable, versioned task.
 *
 * <p>The difference to a typed goal: a task is a named entity with a version. Only that
 * makes it possible to say later which run worked on which task in which revision — the
 * prerequisite for any evaluation across runs.
 *
 * <p>Success criteria ({@code success}) are part of the task because they must be versioned
 * with it: a changed rubric yields different verdicts, and that has to be visible on the run.
 * Repetitions and judges deliberately do not live here yet.
 *
 * @param goal          the text sent to the agent as the user message
 * @param difficulty    level, descriptive only for now
 * @param soul          SOUL default of the task, {@code null} = caller or global default decides
 * @param provider      provider default, {@code null} = caller or global default decides
 * @param profile       strategy profile default, {@code null} = caller or {@code baseline}
 * @param maxToolCalls  budget default of the task, {@code null} = global setting
 * @param maxDurationMs budget default of the task, {@code null} = global setting
 * @param success       deterministically checkable criteria; empty = the run is not graded
 * @param sections      further sections of the file, passed through unchanged
 * @param source        origin file, for provenance
 */
public record Task(
		String id,
		String name,
		int version,
		String description,
		int difficulty,
		List<String> tags,
		String soul,
		String provider,
		String profile,
		Integer maxToolCalls,
		Long maxDurationMs,
		List<Criterion> success,
		String goal,
		Map<String, String> sections,
		String source) {
}
