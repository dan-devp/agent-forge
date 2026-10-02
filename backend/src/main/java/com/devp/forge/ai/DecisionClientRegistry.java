package com.devp.forge.ai;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.devp.forge.ai.ChatClientRegistry.AiNotConfiguredException;
import com.devp.forge.model.Model;
import com.devp.forge.model.ModelKind;
import com.devp.forge.model.ModelRepository;
import com.devp.forge.secrets.SecretResolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.SystemOneRequest;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springframework.stereotype.Component;

/**
 * Builds one TypeSafe client per decision model version, on demand — the counterpart of
 * {@link ChatClientRegistry} and the only class that imports the TypeSafe SDK.
 *
 * <p>Configuration checks and the base-URL override are the chat registry's: they are about
 * the model file, not the protocol. Model files carry the URL with its version path
 * ({@code https://api.typesafe.ai/v1}) like every other model; the SDK wants the API root
 * and appends {@code /v1/systemone} itself, so the version path is cut off here. The same
 * applies to the override, which keeps tests on the dead address.
 */
@Component
public class DecisionClientRegistry {

	private static final Logger log = LoggerFactory.getLogger(DecisionClientRegistry.class);

	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	private final ChatClientRegistry chat;
	private final SecretResolver secrets;
	private final Map<String, TypeSafeClient> clients = new ConcurrentHashMap<>();

	public DecisionClientRegistry(ChatClientRegistry chat, SecretResolver secrets, ModelRepository models) {
		this.chat = chat;
		this.secrets = secrets;
		models.onChange(clients::clear);
	}

	/**
	 * Asks every question about the same state in one call.
	 *
	 * @param state     what is judged, as named fields
	 * @param questions yes/no questions by key, in order
	 * @return per key a truth value between 0 (no) and 1 (yes)
	 */
	public Decisions ask(Model model, Map<String, String> state, Map<String, String> questions) {
		if (model.kind() != ModelKind.DECISION) {
			throw new IllegalArgumentException("model '%s' is not a decision model".formatted(model.id()));
		}
		if (!chat.isConfigured(model)) {
			throw new AiNotConfiguredException("Model '%s' is not usable: %s".formatted(model.id(), chat.problem(model)));
		}
		TypeSafeClient client = clients.computeIfAbsent(model.id() + "@" + model.version(), key -> build(model));

		SystemOneRequest.Builder request = SystemOneRequest.builder().state(state).model(model.modelId());
		questions.forEach((key, text) -> request.question(key, Noul.of(text)));
		SystemOneResponse response = client.systemOne(request.build());

		Map<String, Double> values = new LinkedHashMap<>();
		questions.keySet().forEach(key -> values.put(key, response.noulValue(key)));
		Integer tokens = response.usage() == null ? null : response.usage().totalTokens();
		return new Decisions(values, response.model() == null ? model.modelId() : response.model(), tokens);
	}

	private TypeSafeClient build(Model model) {
		String root = apiRoot(chat.effectiveBaseUrl(model));
		log.info("Decision client built for model '{}' v{}: {} ({})", model.id(), model.version(), root,
				model.modelId());
		return TypeSafeClient.builder()
				.baseUrl(root)
				.apiKey(secrets.value(model.apiKeyEnv()).orElseThrow())
				.defaultModel(model.modelId())
				.timeout(TIMEOUT)
				.build();
	}

	/** {@code https://api.typesafe.ai/v1} becomes {@code https://api.typesafe.ai}. */
	static String apiRoot(String baseUrl) {
		String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		return url.endsWith("/v1") ? url.substring(0, url.length() - 3) : url;
	}

	/** @param model the model that answered, as the endpoint reported it */
	public record Decisions(Map<String, Double> values, String model, Integer tokens) {
	}
}
