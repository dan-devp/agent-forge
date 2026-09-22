package com.devp.forge.api;

import java.util.List;

import com.devp.forge.secrets.SecretResolver;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Names of the environment variables a model may reference. Names only, never values. */
@RestController
@RequestMapping("/api/secrets")
public class SecretController {

	private final SecretResolver secrets;

	public SecretController(SecretResolver secrets) {
		this.secrets = secrets;
	}

	@GetMapping
	public SecretNames list() {
		return new SecretNames(secrets.prefix(), secrets.names());
	}

	public record SecretNames(String prefix, List<String> names) {
	}
}
