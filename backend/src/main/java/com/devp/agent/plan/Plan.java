package com.devp.agent.plan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A saved comparison: which agent, on which task, under which profiles, how often.
 *
 * <p>The plan is what stays equal between executions; an {@code Experiment} is one execution
 * of it. Repeating a comparison after a profile changed is only meaningful if it is the same
 * setup and not a similar one typed again — that is the whole reason this exists as an entity
 * instead of a form.
 *
 * <p>Unlike SOUL, task, agent and profile this is not a file: a plan is made while working,
 * not authored and versioned in Git.
 *
 * @param profiles        the profiles to compare, without the baseline
 * @param includeBaseline add the default profile as a reference training on every start
 */
public record Plan(
		UUID id,
		String name,
		String description,
		String taskId,
		String agentId,
		String providerId,
		List<String> profiles,
		boolean includeBaseline,
		int repetitions,
		Instant createdAt,
		Instant updatedAt) {
}
