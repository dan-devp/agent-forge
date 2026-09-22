package com.devp.forge.model;

import java.util.Locale;

/**
 * Where a model lives. Fixes the base URL for the well-known clouds so a model called
 * "OpenAI" cannot quietly point elsewhere; leaves it open for gateways and local servers.
 *
 * <p>Anthropic is reached through its OpenAI-compatible endpoint, not the native protocol —
 * one code path for everything, at the price of Anthropic-only features.
 */
public enum Provider {

	OPENAI("openai", "https://api.openai.com/v1", "API_KEY_OPENAI", false),
	ANTHROPIC("anthropic", "https://api.anthropic.com/v1", "API_KEY_ANTHROPIC", false),
	/** Gateway or any other OpenAI-compatible server with a key. */
	CUSTOM("custom", null, null, false),
	/** OpenAI-compatible server without a key. */
	LOCAL("local", null, null, true);

	private final String key;
	private final String fixedBaseUrl;
	private final String defaultKeyEnv;
	private final boolean local;

	Provider(String key, String fixedBaseUrl, String defaultKeyEnv, boolean local) {
		this.key = key;
		this.fixedBaseUrl = fixedBaseUrl;
		this.defaultKeyEnv = defaultKeyEnv;
		this.local = local;
	}

	/** Front matter value. */
	public String key() {
		return key;
	}

	/** The one base URL this provider allows, or {@code null} when the file decides. */
	public String fixedBaseUrl() {
		return fixedBaseUrl;
	}

	/** Suggested key variable for the UI, or {@code null}. */
	public String defaultKeyEnv() {
		return defaultKeyEnv;
	}

	public boolean isLocal() {
		return local;
	}

	public static Provider fromKey(String key) {
		for (Provider p : values()) {
			if (p.key.equals(key.strip().toLowerCase(Locale.ROOT))) {
				return p;
			}
		}
		throw new IllegalArgumentException(key);
	}
}
