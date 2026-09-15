package com.devp.agent.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguration aller nutzbaren Modell-Endpunkte.
 *
 * <p>Jeder Eintrag unter {@code app.ai.providers.<id>} ist ein OpenAI-kompatibler
 * Endpunkt — egal ob OpenAI selbst, ein Gateway oder ein lokaler Server
 * (LM Studio, Ollama, vLLM, llama.cpp, LiteLLM). Damit ist "lokal oder Cloud"
 * eine reine Konfigurationsfrage.
 */
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

	/** Provider-Id, die genutzt wird, wenn der Aufruf keine angibt. */
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

		/** Anzeigename im Frontend. */
		private String label;

		/** Vollstaendige Basis-URL inkl. Versionspfad, z. B. https://api.openai.com/v1 */
		private String baseUrl;

		/** API-Key. Lokale Server ignorieren ihn meist, ein Platzhalter genuegt dort. */
		private String apiKey;

		/** Modell-Id am jeweiligen Endpunkt. */
		private String model;

		private Double temperature = 0.7;

		/** true = Provider gilt auch ohne echten API-Key als nutzbar (lokale Server). */
		private boolean local = false;

		/** Provider wird nicht angeboten, wenn false. */
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
		 * Nutzbar konfiguriert? Cloud-Provider brauchen einen echten Key,
		 * lokale Server nur eine erreichbare URL.
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
		 * Erkennt nicht ausgefuellte Keys. Ohne diese Pruefung meldet /api/status
		 * "konfiguriert", obwohl noch der Platzhalter aus .env.example drinsteht —
		 * der Fehler faellt dann erst beim Modellaufruf als 401 auf.
		 */
		private static boolean isPlaceholder(String value) {
			if (value == null || value.isBlank()) {
				return true;
			}
			String normalized = value.trim().toLowerCase();
			return normalized.equals("not-set")
					|| normalized.equals("not-needed")
					|| normalized.equals("changeme")
					|| normalized.endsWith("...");
		}
	}
}
