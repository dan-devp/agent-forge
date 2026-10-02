package com.devp.forge.training;

/** The fate of a step: the baseline is accepted by definition, a candidate by gate or human. */
public enum Decision {
	PENDING,
	ACCEPTED,
	REJECTED
}
