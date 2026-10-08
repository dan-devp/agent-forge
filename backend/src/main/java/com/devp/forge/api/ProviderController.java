package com.devp.forge.api;

import java.util.Arrays;
import java.util.List;

import com.devp.forge.model.Provider;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The providers a model can live at, with what each one fixes — the UI builds its form from it. */
@RestController
@RequestMapping("/api/providers")
public class ProviderController {

	@GetMapping
	public List<ProviderInfo> list() {
		return Arrays.stream(Provider.values())
				.map(p -> new ProviderInfo(p.key(), label(p), p.fixedBaseUrl(), p.defaultKeyEnv(), p.isLocal(),
						p.kind().key()))
				.toList();
	}

	private static String label(Provider p) {
		return switch (p) {
			case OPENAI -> "OpenAI";
			case ANTHROPIC -> "Anthropic";
			case CUSTOM -> "Gateway / custom";
			case LOCAL -> "Local";
			case TYPESAFE -> "TypeSafe (Decision)";
		};
	}

	/** {@code kind}: chat | decision — decision models only judge, they cannot drive an agent. */
	public record ProviderInfo(String key, String label, String fixedBaseUrl, String defaultKeyEnv, boolean local,
			String kind) {
	}
}
