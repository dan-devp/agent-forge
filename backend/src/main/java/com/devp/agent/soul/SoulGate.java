package com.devp.agent.soul;

/**
 * Mandatory question the agent answers for itself before replying.
 *
 * <p>Gates are the difference between "be precise" as a wish and as a rule: a blocking gate
 * must not be overridden by an answer that violates it (gstack pattern).
 *
 * @param blocking true = on a negative answer the agent must not deliver, but ask back or
 *                 name the gap.
 */
public record SoulGate(String id, String question, boolean blocking) {
}
