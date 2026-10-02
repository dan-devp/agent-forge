package com.devp.forge.model;

import java.util.Locale;

/**
 * What a model can be asked. Follows from the provider, so a model file cannot claim the
 * wrong kind.
 */
public enum ModelKind {

	/** Generates text and calls tools: can drive an agent and judge in prose. */
	CHAT,

	/** Answers typed questions with numbers, never text: can only judge. */
	DECISION;

	/** JSON and UI value. */
	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}
}
