package com.devp.agent.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.devp.agent.config.AiProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/**
 * Builds one {@link ChatClient} per configured endpoint.
 *
 * <p>All endpoints speak the OpenAI protocol and differ only in base URL, key and model id.
 * A local server is therefore not a special case but another entry in
 * {@code app.ai.providers}.
 */
@Component
public class ChatClientRegistry {

	private static final Logger log = LoggerFactory.getLogger(ChatClientRegistry.class);

	private final AiProperties properties;
	private final ToolCallingManager toolCallingManager;
	private final Map<String, ChatClient> clients = new LinkedHashMap<>();

	public ChatClientRegistry(AiProperties properties, ToolCallingManager toolCallingManager) {
		this.properties = properties;
		this.toolCallingManager = toolCallingManager;

		properties.getProviders().forEach((id, provider) -> {
			if (!provider.isEnabled()) {
				log.info("AI provider '{}' is disabled", id);
				return;
			}
			clients.put(id, build(provider));
			log.info("AI provider '{}' registered: {} (model {}, configured={})",
					id, provider.getBaseUrl(), provider.getModel(), provider.isConfigured());
		});

		if (clients.isEmpty()) {
			log.warn("No AI provider configured — /api/chat will answer with 503");
		}
	}

	private ChatClient build(AiProperties.Provider provider) {
		OpenAiChatOptions options = OpenAiChatOptions.builder()
				.baseUrl(provider.getBaseUrl())
				// Local servers usually ignore the key, but the SDK insists on a value.
				.apiKey(provider.getApiKey() == null || provider.getApiKey().isBlank()
						? "not-set"
						: provider.getApiKey())
				.model(provider.getModel())
				.temperature(provider.getTemperature())
				.build();

		OpenAiChatModel model = OpenAiChatModel.builder()
				.options(options)
				.build();

		// No defaultSystem: the system prompt comes from the SOUL per call, so a reload takes
		// effect immediately and different SOULs can share one client.
		//
		// In Spring AI 2.0 the tool loop is driven by the ToolCallingAdvisor, not the
		// ChatModel. The ChatClient would register one with the default manager on its own;
		// here the tracing manager is plugged in instead — otherwise there would be no trace.
		return ChatClient.builder(model)
				.defaultAdvisors(ToolCallingAdvisor.builder()
						.toolCallingManager(toolCallingManager)
						.build())
				.build();
	}

	/** Resolution including fallback to the default provider. */
	public ResolvedProvider resolve(String requestedId) {
		String id = (requestedId == null || requestedId.isBlank())
				? properties.getDefaultProvider()
				: requestedId;

		ChatClient client = clients.get(id);
		AiProperties.Provider config = properties.getProviders().get(id);

		if (client == null || config == null) {
			throw new UnknownProviderException(
					"Unknown provider '%s'. Available: %s".formatted(id, clients.keySet()));
		}
		return new ResolvedProvider(id, config, client);
	}

	/** All registered providers for display in the frontend. */
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

	/** The requested provider id does not exist. */
	public static class UnknownProviderException extends RuntimeException {
		public UnknownProviderException(String message) {
			super(message);
		}
	}
}
