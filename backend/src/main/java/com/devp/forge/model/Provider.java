package com.devp.forge.model;

import java.util.Locale;

/**
 * Where a model lives. Fixes the base URL for the well-known clouds so a model called
 * "OpenAI" cannot quietly point elsewhere; leaves it open for gateways and local servers.
 *
 * <p>Anthropic is reached through its OpenAI-compatible endpoint, not the native protocol —
 * one code path for everything, at the price of Anthropic-only features.
 *
 * <p>TypeSafe serves decision models: typed questions in, numbers out, no text. Such a model
 * can judge but cannot drive an agent.
 */
public enum Provider {

	OPENAI("openai", "https://api.openai.com/v1", "API_KEY_OPENAI", false, ModelKind.CHAT),
	ANTHROPIC("anthropic", "https://api.anthropic.com/v1", "API_KEY_ANTHROPIC", false, ModelKind.CHAT),
	/** Gateway or any other OpenAI-compatible server with a key. */
	CUSTOM("custom", null, null, false, ModelKind.CHAT),
	/** OpenAI-compatible server without a key. */
	LOCAL("local", null, null, true, ModelKind.CHAT),
	/** TypeSafe System One API (Jev). The version path is part of the URL like everywhere else. */
	TYPESAFE("typesafe", "https://api.typesafe.ai/v1", "API_KEY_TYPESAFE", false, ModelKind.DECISION);

	private final String key;
	private final String fixedBaseUrl;
	private final String defaultKeyEnv;
	private final boolean local;
	private final ModelKind kind;

	Provider(String key, String fixedBaseUrl, String defaultKeyEnv, boolean local, ModelKind kind) {
		this.key = key;
		this.fixedBaseUrl = fixedBaseUrl;
		this.defaultKeyEnv = defaultKeyEnv;
		this.local = local;
		this.kind = kind;
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

	public ModelKind kind() {
		return kind;
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
