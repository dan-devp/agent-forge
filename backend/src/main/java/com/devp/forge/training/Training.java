package com.devp.forge.training;

import java.util.List;

import com.devp.forge.store.Versioned;

/**
 * A training: which agent learns, who coaches it, on which tasks, and how much it may cost.
 *
 * <p>The split into train and holdout belongs here, not to the task: the same task can be
 * practice in one training and the exam in the next. The coach sees the failures of the
 * train tasks only; the holdout tasks tell whether it taught or merely fitted.
 *
 * @param agentId     the agent that learns; every candidate becomes a new version of it
 * @param coachId     an agent whose system prompt makes it the coach; its model writes the proposals
 * @param repetitions runs per task and agent version — the n of k/n
 * @param maxSteps    coach proposals at most, the baseline not counted
 * @param maxTokens   cost budget over runs, judges and coach; {@code null} = no limit
 * @param goal        the body: what the coach should aim for, in prose; may be blank
 */
public record Training(
		String id,
		int version,
		String name,
		String description,
		String agentId,
		String coachId,
		List<String> train,
		List<String> holdout,
		int repetitions,
		int maxSteps,
		Integer maxTokens,
		Approval approval,
		String goal) implements Versioned {

	public Training {
		train = List.copyOf(train);
		holdout = List.copyOf(holdout);
	}

	/** Every task the training measures, train first. */
	public List<String> allTasks() {
		return java.util.stream.Stream.concat(train.stream(), holdout.stream()).toList();
	}
}
