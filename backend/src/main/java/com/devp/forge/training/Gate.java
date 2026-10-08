package com.devp.forge.training;

/**
 * Whether a candidate is better than the best version so far. Measurement, not belief.
 *
 * <p>Better means: the train rate rises, and the holdout rate does not fall. Equal on train
 * is not better — with a handful of runs a tie is noise, and a changed prompt that does not
 * help is a change nobody needs. The holdout guards against a coach that fits the train
 * tasks at the expense of everything else.
 */
public final class Gate {

	private Gate() {
	}

	public static Verdict decide(Score best, Score candidate) {
		if (candidate.trainTotal() == 0) {
			return new Verdict(false, "No train runs measured");
		}
		if (candidate.trainRate() <= best.trainRate()) {
			return new Verdict(false, "Train not better: %s vs %s".formatted(candidate.train(), best.train()));
		}
		if (best.hasHoldout() && candidate.holdoutRate() < best.holdoutRate()) {
			return new Verdict(false, "Holdout worse: %s vs %s - learned by heart instead of improved"
					.formatted(candidate.holdout(), best.holdout()));
		}
		String holdout = best.hasHoldout() ? ", holdout %s -> %s".formatted(best.holdout(), candidate.holdout()) : "";
		return new Verdict(true, "Train %s -> %s%s".formatted(best.train(), candidate.train(), holdout));
	}

	public record Verdict(boolean accepted, String reason) {
	}
}
