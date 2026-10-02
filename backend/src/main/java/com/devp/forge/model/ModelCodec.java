package com.devp.forge.model;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import com.devp.forge.doc.MarkdownDocument;
import com.devp.forge.store.EntityCodec;
import com.devp.forge.store.FileStore;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;

/**
 * <pre>
 * ---
 * id: gateway
 * version: 1
 * name: Gateway (LiteLLM)
 * provider: custom        # openai | anthropic | custom | local | typesafe
 * base-url: https://litellm.example.com/v1
 * model: gpt-4o-mini
 * api-key-env: API_KEY_LITELLM
 * temperature: 0.7
 * ---
 * </pre>
 *
 * <p>For {@code openai} and {@code anthropic} the base URL is fixed; a file that says
 * otherwise is rejected. Older files without {@code provider} are read as {@code local}
 * when they say {@code local: true}, else {@code custom}.
 */
public final class ModelCodec implements EntityCodec<Model> {

	@Override
	public String kind() {
		return "models";
	}

	@Override
	public Model decode(MarkdownDocument d) {
		Provider provider = provider(d);
		String baseUrl = provider.fixedBaseUrl() != null
				? d.text("base-url", provider.fixedBaseUrl())
				: FileStore.required(d, "base-url");
		if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
			throw new EntityInvalidException("'base-url' must start with http:// or https://");
		}
		if (provider.fixedBaseUrl() != null && !baseUrl.equals(provider.fixedBaseUrl())) {
			throw new EntityInvalidException("'base-url' for provider %s must be %s".formatted(provider.key(),
					provider.fixedBaseUrl()));
		}
		String apiKeyEnv = d.text("api-key-env");
		if (!provider.isLocal() && apiKeyEnv == null) {
			throw new EntityInvalidException("'api-key-env' is required unless provider is local");
		}
		Double temperature = d.decimal("temperature");
		if (temperature != null && (temperature < 0 || temperature > 2)) {
			throw new EntityInvalidException("'temperature' must be between 0 and 2");
		}
		return new Model(
				d.id(),
				d.integer("version", 1),
				FileStore.required(d, "name"),
				d.text("description"),
				provider,
				baseUrl,
				FileStore.required(d, "model"),
				apiKeyEnv,
				temperature == null ? 0.7 : temperature);
	}

	private static Provider provider(MarkdownDocument d) {
		String key = d.text("provider");
		if (key == null) {
			return d.bool("local", false) ? Provider.LOCAL : Provider.CUSTOM;
		}
		try {
			return Provider.fromKey(key);
		}
		catch (IllegalArgumentException ex) {
			throw new EntityInvalidException("'provider' must be one of %s — was '%s'".formatted(
					Arrays.stream(Provider.values()).map(Provider::key).toList(), key));
		}
	}

	@Override
	public Map<String, Object> frontMatter(Model m) {
		Map<String, Object> fm = new LinkedHashMap<>();
		fm.put("id", m.id());
		fm.put("version", m.version());
		fm.put("name", m.name());
		if (m.description() != null && !m.description().isBlank()) {
			fm.put("description", m.description());
		}
		fm.put("provider", m.provider().key());
		fm.put("base-url", m.baseUrl());
		fm.put("model", m.modelId());
		if (m.apiKeyEnv() != null && !m.apiKeyEnv().isBlank()) {
			fm.put("api-key-env", m.apiKeyEnv());
		}
		fm.put("temperature", m.temperature());
		return fm;
	}

	@Override
	public String body(Model entity) {
		return null;
	}

	@Override
	public Model withVersion(Model m, int version) {
		return new Model(m.id(), version, m.name(), m.description(), m.provider(), m.baseUrl(), m.modelId(),
				m.apiKeyEnv(), m.temperature());
	}
}
