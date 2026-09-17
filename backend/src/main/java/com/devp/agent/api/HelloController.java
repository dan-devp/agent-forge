package com.devp.agent.api;

import java.time.Instant;
import java.util.List;

import com.devp.agent.ai.ChatClientRegistry;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Smoke-test endpoints without a model call — they work without an API key. */
@RestController
@RequestMapping("/api")
public class HelloController {

	private final ChatClientRegistry registry;

	public HelloController(ChatClientRegistry registry) {
		this.registry = registry;
	}

	@GetMapping("/hello")
	public HelloResponse hello(@RequestParam(defaultValue = "Welt") String name) {
		return new HelloResponse("Hallo, %s!".formatted(name), Instant.now());
	}

	/** Which providers are available? Used by the frontend for the selection. */
	@GetMapping("/status")
	public StatusResponse status() {
		return new StatusResponse(registry.defaultProvider(), registry.list());
	}

	public record HelloResponse(String message, Instant timestamp) {
	}

	public record StatusResponse(String defaultProvider, List<ChatClientRegistry.ProviderInfo> providers) {
	}
}
