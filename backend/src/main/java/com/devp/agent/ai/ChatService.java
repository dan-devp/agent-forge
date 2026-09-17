package com.devp.agent.ai;

import com.devp.agent.ai.ChatClientRegistry.ResolvedProvider;
import com.devp.agent.soul.Soul;
import com.devp.agent.soul.SoulRenderer;
import com.devp.agent.soul.SoulRepository;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * Thin facade over Spring AI. The only place where the prototype touches the model SDK —
 * everything above stays provider-independent.
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
	 * One turn against the requested provider, guided by the requested SOUL.
	 *
	 * <p>The SOUL is resolved and rendered per call, not when the client is built —
	 * otherwise {@code /api/souls/reload} would only take effect after a restart.
	 *
	 * @param providerId provider id or {@code null} for the default
	 * @param soulId     SOUL id or {@code null} for the active SOUL
	 */
	public ChatResult ask(String message, String providerId, String soulId) {
		ResolvedProvider provider = registry.resolve(providerId);
		Soul soul = souls.resolve(soulId);

		if (!provider.config().isConfigured()) {
			throw new AiNotConfiguredException(
					"Provider '%s' is not fully configured (check base URL, model and API key)."
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
					"Call to provider '%s' (%s) failed: %s"
							.formatted(provider.id(), provider.config().getBaseUrl(), ex.getMessage()),
					ex);
		}
		long durationMs = System.currentTimeMillis() - startedAt;

		if (response == null || response.getResult() == null) {
			throw new ModelCallFailedException(
					"Provider '%s' returned an empty response.".formatted(provider.id()), null);
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
	 * The model's answer including provenance and cost metadata.
	 *
	 * <p>{@code soul} and {@code soulVersion} are provenance: a later evaluation must be able
	 * to refer to the identity under which the answer was produced.
	 */
	public record ChatResult(String answer, String provider, String model, String soul, int soulVersion,
			Integer totalTokens, long durationMs) {
	}

	/** Provider is not usably configured (e.g. missing API key). */
	public static class AiNotConfiguredException extends RuntimeException {
		public AiNotConfiguredException(String message) {
			super(message);
		}
	}

	/** The endpoint was supposed to be reachable but did not deliver. */
	public static class ModelCallFailedException extends RuntimeException {
		public ModelCallFailedException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
