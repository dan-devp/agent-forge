package com.devp.agent.strategy;

import java.util.List;
import java.util.Optional;

import com.devp.agent.profile.Profile.StrategyConfig;
import com.devp.agent.run.AgentRun;
import com.devp.agent.run.RunScore;
import com.devp.agent.run.RunStep;

/**
 * A continual-learning strategy with frozen model weights.
 *
 * <p>Every known approach differs only in <em>what</em> is stored, <em>when</em> it is read
 * and <em>when</em> it is written. Five hooks into the run loop cover all of them; a
 * strategy implements the ones it needs and leaves the rest at their empty default.
 * Strategies are Spring beans; their per-profile parameters arrive with every call as
 * {@link StrategyConfig}, so one bean serves many profiles.
 *
 * <p>A strategy is activated by a profile, never by code. Which strategies ran is thereby
 * recorded on the run through {@code profileId}/{@code profileVersion}.
 */
public interface Strategy {

	/** Id as written under {@code strategies:} in a profile file. */
	String id();

	/** Before the first model round: add to the prompt, offer tools. */
	default void beforeRun(RunContext context, StrategyConfig config) {
	}

	/** After every executed tool round, with the steps just written. Observe, do not block. */
	default void afterToolRound(RunContext context, StrategyConfig config, List<RunStep> steps) {
	}

	/**
	 * When the model stops requesting tools, before the run is marked DONE.
	 *
	 * @return feedback to send back as a new user message if the answer must not stand as
	 *         final; empty to accept. The loop is bounded by {@code max-rounds} in the
	 *         config and by the run's budget, whichever hits first.
	 */
	default Optional<String> beforeFinish(RunContext context, StrategyConfig config, String answer) {
		return Optional.empty();
	}

	/** After the run has been written and graded. This is where learning is persisted. */
	default void afterRun(RunContext context, StrategyConfig config, AgentRun run, List<RunScore> scores) {
	}

	/**
	 * Between runs, scheduled: consolidate, deduplicate, retire. No trigger exists yet — the
	 * hook is declared so that a strategy can already say what it would do there.
	 */
	default void betweenRuns(StrategyConfig config) {
	}
}
