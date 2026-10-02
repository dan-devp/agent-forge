package com.devp.forge.training;

/**
 * k/n on the train tasks and on the holdout tasks of one measured agent version.
 *
 * <p>Totals count every run that ran, whatever its end state: a run that failed or blew its
 * budget missed its evals, and that belongs in the number. Cancelled runs never ran and are
 * not counted.
 */
public record Score(int trainPassed, int trainTotal, int holdoutPassed, int holdoutTotal) {

	public double trainRate() {
		return trainTotal == 0 ? 0 : (double) trainPassed / trainTotal;
	}

	public double holdoutRate() {
		return holdoutTotal == 0 ? 0 : (double) holdoutPassed / holdoutTotal;
	}

	public boolean hasHoldout() {
		return holdoutTotal > 0;
	}

	/** Nothing left to learn from: every train run passed. The coach never sees the holdout. */
	public boolean trainPerfect() {
		return trainTotal > 0 && trainPassed == trainTotal;
	}

	public String train() {
		return trainPassed + "/" + trainTotal;
	}

	public String holdout() {
		return holdoutPassed + "/" + holdoutTotal;
	}
}
