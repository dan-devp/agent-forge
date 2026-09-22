package com.devp.forge.secrets;

import java.util.List;
import java.util.Optional;

import com.devp.forge.config.SecretProperties;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Keys live in the environment, files only name them.
 *
 * <p>{@link #names()} is what the UI may see: variable names with the configured prefix,
 * never values. {@link #value(String)} is for the model call only and is never serialized.
 * Resolution goes through Spring's {@link Environment}, so a test can set a key as a
 * property without touching the process environment.
 */
@Component
public class SecretResolver {

	private final SecretProperties properties;
	private final Environment environment;

	public SecretResolver(SecretProperties properties, Environment environment) {
		this.properties = properties;
		this.environment = environment;
	}

	/** Names of the variables a model file may reference, sorted. */
	public List<String> names() {
		String prefix = properties.getEnvPrefix();
		return System.getenv().keySet().stream()
				.filter(name -> name.startsWith(prefix))
				.filter(name -> isSet(name))
				.sorted()
				.toList();
	}

	public boolean isSet(String name) {
		return value(name).isPresent();
	}

	public Optional<String> value(String name) {
		if (name == null || name.isBlank()) {
			return Optional.empty();
		}
		String value = environment.getProperty(name);
		return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
	}

	public String prefix() {
		return properties.getEnvPrefix();
	}
}
