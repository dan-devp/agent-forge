package com.devp.agent.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.devp.agent.config.AiProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/**
 * Baut je konfiguriertem Endpunkt einen {@link ChatClient}.
 *
 * <p>Alle Endpunkte sprechen das OpenAI-Protokoll, unterscheiden sich also nur in
 * Basis-URL, Key und Modell-Id. Ein lokaler Server ist damit kein Sonderfall,
 * sondern ein weiterer Eintrag in {@code app.ai.providers}.
 */
@Component
public class ChatClientRegistry {

	private static final Logger log = LoggerFactory.getLogger(ChatClientRegistry.class);

	private final AiProperties properties;
	private final Map<String, ChatClient> clients = new LinkedHashMap<>();

	public ChatClientRegistry(AiProperties properties) {
		this.properties = properties;

		properties.getProviders().forEach((id, provider) -> {
			if (!provider.isEnabled()) {
				log.info("AI-Provider '{}' ist deaktiviert", id);
				return;
			}
			clients.put(id, build(provider));
			log.info("AI-Provider '{}' registriert: {} (Modell {}, konfiguriert={})",
					id, provider.getBaseUrl(), provider.getModel(), provider.isConfigured());
		});

		if (clients.isEmpty()) {
			log.warn("Kein AI-Provider konfiguriert — /api/chat wird mit 503 antworten");
		}
	}

	private ChatClient build(AiProperties.Provider provider) {
		OpenAiChatOptions options = OpenAiChatOptions.builder()
				.baseUrl(provider.getBaseUrl())
				// Lokale Server pruefen den Key meist nicht, das SDK verlangt aber einen Wert.
				.apiKey(provider.getApiKey() == null || provider.getApiKey().isBlank()
						? "not-set"
						: provider.getApiKey())
				.model(provider.getModel())
				.temperature(provider.getTemperature())
				.build();

		OpenAiChatModel model = OpenAiChatModel.builder()
				.options(options)
				.build();

		// Kein defaultSystem: der System-Prompt kommt pro Aufruf aus der SOUL, damit ein
		// Reload sofort wirkt und verschiedene SOULs denselben Client nutzen koennen.
		return ChatClient.builder(model).build();
	}

	/** Aufloesung inkl. Fallback auf den Default-Provider. */
	public ResolvedProvider resolve(String requestedId) {
		String id = (requestedId == null || requestedId.isBlank())
				? properties.getDefaultProvider()
				: requestedId;

		ChatClient client = clients.get(id);
		AiProperties.Provider config = properties.getProviders().get(id);

		if (client == null || config == null) {
			throw new UnknownProviderException(
					"Unbekannter Provider '%s'. Verfuegbar: %s".formatted(id, clients.keySet()));
		}
		return new ResolvedProvider(id, config, client);
	}

	/** Alle registrierten Provider fuer die Anzeige im Frontend. */
	public List<ProviderInfo> list() {
		return clients.keySet().stream()
				.map(id -> {
					AiProperties.Provider p = properties.getProviders().get(id);
					return new ProviderInfo(
							id,
							p.getLabel() == null ? id : p.getLabel(),
							p.getModel(),
							p.isLocal(),
							p.isConfigured(),
							id.equals(properties.getDefaultProvider()));
				})
				.toList();
	}

	public String defaultProvider() {
		return properties.getDefaultProvider();
	}

	public record ResolvedProvider(String id, AiProperties.Provider config, ChatClient client) {
	}

	public record ProviderInfo(String id, String label, String model, boolean local, boolean configured,
			boolean isDefault) {
	}

	/** Angeforderte Provider-Id existiert nicht. */
	public static class UnknownProviderException extends RuntimeException {
		public UnknownProviderException(String message) {
			super(message);
		}
	}
}
