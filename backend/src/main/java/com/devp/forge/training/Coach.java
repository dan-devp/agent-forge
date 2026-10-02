package com.devp.forge.training;

import com.devp.forge.agent.Agent;

/**
 * Turns the evidence of one measurement into a better system prompt.
 *
 * <p>The coach is an agent like any other — model plus system prompt, versioned, editable,
 * and one day trainable itself. An interface so the engine can be tested without a model.
 */
public interface Coach {

	/** Throws when the call fails; an answer without a usable prompt is a proposal with {@code prompt == null}. */
	Proposal propose(Agent coach, CoachBrief brief);
}
