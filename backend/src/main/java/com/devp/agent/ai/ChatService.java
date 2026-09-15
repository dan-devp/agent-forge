package com.devp.agent.ai;

import com.devp.agent.ai.ChatClientRegistry.ResolvedProvider;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * Duenne Fassade vor Spring AI. Einziger Ort, an dem der Prototyp das
 * Modell-SDK beruehrt — alles darueber bleibt provider-unabhaengig.
 */
@Service
public class ChatService {

	private final ChatClientRegistry registry;

	public ChatService(ChatClientRegistry registry) {
		this.registry = registry;
	}

	/**
	 * Ein Turn gegen den gewuenschten Provider.
	 *
	 * @param providerId Provider-Id oder {@code null} fuer den Default
	 */
	public ChatResult ask(String message, String providerId) {
		ResolvedProvider provider = registry.resolve(providerId);

		if (!provider.config().isConfigured()) {
			throw new AiNotConfiguredException(
					"Provider '%s' ist nicht vollstaendig konfiguriert (Basis-URL, Modell und ggf. API-Key pruefen)."
							.formatted(provider.id()));
		}

		long startedAt = System.currentTimeMillis();
		ChatResponse response;
		try {
			response = provider.client().prompt()
					.user(message)
					.call()
					.chatResponse();
		}
		catch (RuntimeException ex) {
			throw new ModelCallFailedException(
					"Aufruf von Provider '%s' (%s) fehlgeschlagen: %s"
							.formatted(provider.id(), provider.config().getBaseUrl(), ex.getMessage()),
					ex);
		}
		long durationMs = System.currentTimeMillis() - startedAt;

		if (response == null || response.getResult() == null) {
			throw new ModelCallFailedException(
					"Provider '%s' lieferte eine leere Antwort.".formatted(provider.id()), null);
		}

		String model = response.getMetadata().getModel();
		Integer totalTokens = response.getMetadata().getUsage() != null
				? response.getMetadata().getUsage().getTotalTokens()
				: null;

		return new ChatResult(
				response.getResult().getOutput().getText(),
				provider.id(),
				model == null || model.isBlank() ? provider.config().getModel() : model,
				totalTokens,
				durationMs);
	}

	/** Antwort des Modells inkl. Herkunfts- und Kostenmetadaten. */
	public record ChatResult(String answer, String provider, String model, Integer totalTokens, long durationMs) {
	}

	/** Provider ist nicht nutzbar konfiguriert (z. B. fehlender API-Key). */
	public static class AiNotConfiguredException extends RuntimeException {
		public AiNotConfiguredException(String message) {
			super(message);
		}
	}

	/** Der Endpunkt war erreichbar-gemeint, hat aber nicht geliefert. */
	public static class ModelCallFailedException extends RuntimeException {
		public ModelCallFailedException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
