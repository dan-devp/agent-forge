package com.devp.agent.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of all usable model endpoints.
 *
 * <p>Every entry under {@code app.ai.providers.<id>} is an OpenAI-compatible endpoint —
 * OpenAI itself, a gateway or a local server (LM Studio, Ollama, vLLM, llama.cpp, LiteLLM).
 * "Local or cloud" is thereby purely a configuration question.
 */
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

	/** Provider id used when the call names none. */
	private String defaultProvider = "openai";

	private Map<String, Provider> providers = new LinkedHashMap<>();

	public String getDefaultProvider() {
		return defaultProvider;
	}

	public void setDefaultProvider(String defaultProvider) {
		this.defaultProvider = defaultProvider;
	}

	public Map<String, Provider> getProviders() {
		return providers;
	}

	public void setProviders(Map<String, Provider> providers) {
		this.providers = providers;
	}

	public static class Provider {

		/** Display name in the frontend. */
		private String label;

		/** Complete base URL including the version path, e.g. https://api.openai.com/v1 */
		private String baseUrl;

		/** API key. Local servers mostly ignore it; a placeholder is enough there. */
		private String apiKey;

		/** Model id at the endpoint. */
		private String model;

		private Double temperature = 0.7;

		/** true = the provider counts as usable without a real API key (local servers). */
		private boolean local = false;

		/** Provider is not offered when false. */
		private boolean enabled = true;

		public String getLabel() {
			return label;
		}

		public void setLabel(String label) {
			this.label = label;
		}

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public String getApiKey() {
			return apiKey;
		}

		public void setApiKey(String apiKey) {
			this.apiKey = apiKey;
		}

		public String getModel() {
			return model;
		}

		public void setModel(String model) {
			this.model = model;
		}

		public Double getTemperature() {
			return temperature;
		}

		public void setTemperature(Double temperature) {
			this.temperature = temperature;
		}

		public boolean isLocal() {
			return local;
		}

		public void setLocal(boolean local) {
			this.local = local;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		/**
		 * Usably configured? Cloud providers need a real key, local servers only a reachable URL.
		 */
		public boolean isConfigured() {
			if (baseUrl == null || baseUrl.isBlank() || model == null || model.isBlank()) {
				return false;
			}
			if (local) {
				return true;
			}
			return !isPlaceholder(apiKey);
		}

		/**
		 * Detects keys that were never filled in. Without this check /api/status reports
		 * "configured" while the placeholder from .env.example is still in place — the error
		 * would only surface as a 401 on the first model call.
		 */
		private static boolean isPlaceholder(String value) {
			if (value == null || value.isBlank()) {
				return true;
			}
			String normalized = value.trim().toLowerCase(Locale.ROOT);
			return normalized.equals("not-set")
					|| normalized.equals("not-needed")
					|| normalized.equals("changeme")
					|| normalized.endsWith("...");
		}
	}
}
