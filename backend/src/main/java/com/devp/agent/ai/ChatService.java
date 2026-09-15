package com.devp.agent.ai;

import com.devp.agent.ai.ChatClientRegistry.ResolvedProvider;
import com.devp.agent.soul.Soul;
import com.devp.agent.soul.SoulRenderer;
import com.devp.agent.soul.SoulRepository;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * Duenne Fassade vor Spring AI. Einziger Ort, an dem der Prototyp das
 * Modell-SDK beruehrt — alles darueber bleibt provider-unabhaengig.
 */
@Service
public class ChatService {

	private final ChatClientRegistry registry;
	private final SoulRepository souls;
	private final SoulRenderer renderer;

	public ChatService(ChatClientRegistry registry, SoulRepository souls, SoulRenderer renderer) {
		this.registry = registry;
		this.souls = souls;
		this.renderer = renderer;
	}

	/**
	 * Ein Turn gegen den gewuenschten Provider, gefuehrt von der gewuenschten SOUL.
	 *
	 * <p>Die SOUL wird pro Aufruf aufgeloest und gerendert, nicht beim Bau des Clients —
	 * sonst wuerde {@code /api/souls/reload} erst nach einem Neustart wirken.
	 *
	 * @param providerId Provider-Id oder {@code null} fuer den Default
	 * @param soulId     SOUL-Id oder {@code null} fuer die aktive SOUL
	 */
	public ChatResult ask(String message, String providerId, String soulId) {
		ResolvedProvider provider = registry.resolve(providerId);
		Soul soul = souls.resolve(soulId);

		if (!provider.config().isConfigured()) {
			throw new AiNotConfiguredException(
					"Provider '%s' ist nicht vollstaendig konfiguriert (Basis-URL, Modell und ggf. API-Key pruefen)."
							.formatted(provider.id()));
		}

		long startedAt = System.currentTimeMillis();
		ChatResponse response;
		try {
			response = provider.client().prompt()
					.system(renderer.render(soul))
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
				soul.id(),
				soul.version(),
				totalTokens,
				durationMs);
	}

	/**
	 * Antwort des Modells inkl. Herkunfts- und Kostenmetadaten.
	 *
	 * <p>{@code soul} und {@code soulVersion} sind Provenienz: eine spaetere Bewertung muss
	 * sich auf die Identitaet beziehen koennen, unter der die Antwort entstanden ist.
	 */
	public record ChatResult(String answer, String provider, String model, String soul, int soulVersion,
			Integer totalTokens, long durationMs) {
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
