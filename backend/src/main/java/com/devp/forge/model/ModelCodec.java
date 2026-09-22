package com.devp.forge.model;

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
 * base-url: https://litellm.example.com/v1
 * model: gpt-4o-mini
 * api-key-env: API_KEY_LITELLM
 * temperature: 0.7
 * local: false
 * ---
 * </pre>
 */
public final class ModelCodec implements EntityCodec<Model> {

	@Override
	public String kind() {
		return "models";
	}

	@Override
	public Model decode(MarkdownDocument d) {
		String baseUrl = FileStore.required(d, "base-url");
		if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
			throw new EntityInvalidException("'base-url' must start with http:// or https://");
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
				baseUrl,
				FileStore.required(d, "model"),
				d.text("api-key-env"),
				temperature == null ? 0.7 : temperature,
				d.bool("local", false));
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
		fm.put("base-url", m.baseUrl());
		fm.put("model", m.modelId());
		if (m.apiKeyEnv() != null && !m.apiKeyEnv().isBlank()) {
			fm.put("api-key-env", m.apiKeyEnv());
		}
		fm.put("temperature", m.temperature());
		fm.put("local", m.local());
		return fm;
	}

	@Override
	public String body(Model entity) {
		return null;
	}

	@Override
	public Model withVersion(Model m, int version) {
		return new Model(m.id(), version, m.name(), m.description(), m.baseUrl(), m.modelId(), m.apiKeyEnv(),
				m.temperature(), m.local());
	}
}
