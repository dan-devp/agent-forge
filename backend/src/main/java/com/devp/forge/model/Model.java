package com.devp.forge.model;

import com.devp.forge.store.Versioned;

/**
 * An OpenAI-compatible endpoint plus the model id served there.
 *
 * <p>Endpoint and model are one entity because that pair is what a run records and what
 * two runs are compared by. A gateway serving five models is five entities sharing a URL.
 *
 * @param baseUrl   complete base URL including the version path, e.g. {@code https://api.openai.com/v1}
 * @param modelId   model id at the endpoint
 * @param provider  where it lives; fixes the base URL for the well-known clouds
 * @param apiKeyEnv name of the environment variable holding the key, or {@code null} for none
 */
public record Model(
		String id,
		int version,
		String name,
		String description,
		Provider provider,
		String baseUrl,
		String modelId,
		String apiKeyEnv,
		double temperature) implements Versioned {

	/** Usable without a key — local servers ignore it. */
	public boolean local() {
		return provider.isLocal();
	}

	/** Needs a key from the environment before it can be called. */
	public boolean needsKey() {
		return !local();
	}
}
