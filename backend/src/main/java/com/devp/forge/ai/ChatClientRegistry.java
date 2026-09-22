package com.devp.forge.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.devp.forge.config.ModelProperties;
import com.devp.forge.model.Model;
import com.devp.forge.model.ModelRepository;
import com.devp.forge.secrets.SecretResolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/**
 * Builds one {@link ChatClient} per model version, on demand.
 *
 * <p>The only class that imports Spring AI's OpenAI package. All endpoints speak the OpenAI
 * protocol and differ only in URL, key and model id, so a local server is not a special
 * case. Clients are cached by id and version and dropped whenever the model store changes.
 *
 * <p>In Spring AI 2.0 the tool loop is driven by the {@code ToolCallingAdvisor}, not the
 * ChatModel. Without our own advisor the ChatClient would register one with the default
 * manager — and there would be no trace.
 */
@Component
public class ChatClientRegistry {

	private static final Logger log = LoggerFactory.getLogger(ChatClientRegistry.class);

	/** What the SDK gets when a model needs no key: it insists on a non-blank value. */
	private static final String NO_KEY = "not-set";

	private final ModelRepository models;
	private final SecretResolver secrets;
	private final ModelProperties properties;
	private final ToolCallingManager toolCallingManager;
	private final Map<String, ChatClient> clients = new ConcurrentHashMap<>();

	public ChatClientRegistry(ModelRepository models, SecretResolver secrets, ModelProperties properties,
			ToolCallingManager toolCallingManager) {
		this.models = models;
		this.secrets = secrets;
		this.properties = properties;
		this.toolCallingManager = toolCallingManager;
		models.onChange(clients::clear);
		if (properties.hasOverride()) {
			log.warn("All model calls are redirected to {} (app.models.base-url-override)",
					properties.getBaseUrlOverride());
		}
	}

	/** Current version of the model by id, or {@link com.devp.forge.store.StoreExceptions.UnknownEntityException}. */
	public Model model(String id) {
		return models.get(id);
	}

	/**
	 * Whether a call could succeed: URL and model id present, and a key where one is needed.
	 * Checked before every run so the failure is a clear 503, not a 401 after the prompt was
	 * already built.
	 */
	public boolean isConfigured(Model model) {
		if (model.baseUrl() == null || model.baseUrl().isBlank() || model.modelId() == null
				|| model.modelId().isBlank()) {
			return false;
		}
		return !model.needsKey() || secrets.isSet(model.apiKeyEnv());
	}

	/** Why {@link #isConfigured} is false, for the API. */
	public String problem(Model model) {
		if (isConfigured(model)) {
			return null;
		}
		if (model.apiKeyEnv() == null || model.apiKeyEnv().isBlank()) {
			return "needs an API key: set 'api-key-env' or mark the model as local";
		}
		return "environment variable '%s' is not set".formatted(model.apiKeyEnv());
	}

	public ChatClient client(Model model) {
		if (!isConfigured(model)) {
			throw new AiNotConfiguredException(
					"Model '%s' is not usable: %s".formatted(model.id(), problem(model)));
		}
		return clients.computeIfAbsent(model.id() + "@" + model.version(), key -> build(model));
	}

	/** The URL a call would actually go to — the override wins over the file. */
	public String effectiveBaseUrl(Model model) {
		return properties.hasOverride() ? properties.getBaseUrlOverride() : model.baseUrl();
	}

	private ChatClient build(Model model) {
		OpenAiChatOptions options = OpenAiChatOptions.builder()
				.baseUrl(effectiveBaseUrl(model))
				.apiKey(secrets.value(model.apiKeyEnv()).orElse(NO_KEY))
				.model(model.modelId())
				.temperature(model.temperature())
				.build();

		OpenAiChatModel chatModel = OpenAiChatModel.builder().options(options).build();
		log.info("Chat client built for model '{}' v{}: {} ({})", model.id(), model.version(),
				effectiveBaseUrl(model), model.modelId());

		return ChatClient.builder(chatModel)
				.defaultAdvisors(ToolCallingAdvisor.builder().toolCallingManager(toolCallingManager).build())
				.build();
	}

	/** The model needs a key that is not in the environment, or is otherwise incomplete. */
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
