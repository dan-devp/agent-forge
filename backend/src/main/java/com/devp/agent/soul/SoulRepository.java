package com.devp.agent.soul;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.devp.agent.config.SoulProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Loads all SOUL definitions from the configured locations and keeps them in memory.
 *
 * <p>The set is replaceable at runtime via {@link #reload()} — a changed SOUL must take
 * effect without a restart, otherwise the later learning loop cannot be observed.
 */
@Component
public class SoulRepository {

	private static final Logger log = LoggerFactory.getLogger(SoulRepository.class);

	/** Applies when no definition was found — the agent stays able to answer. */
	private static final Soul FALLBACK = new Soul(
			"default",
			"Agent",
			"KI-Mitarbeiter",
			0,
			"Built-in fallback because no SOUL file was found.",
			List.of(),
			Map.of("Identitaet", "Du bist ein hilfreicher KI-Mitarbeiter. Antworte praezise und knapp."),
			"built-in");

	private final SoulProperties properties;
	private final ResourcePatternResolver resolver;

	private volatile Map<String, Soul> souls = Map.of();

	public SoulRepository(SoulProperties properties) {
		this.properties = properties;
		this.resolver = new PathMatchingResourcePatternResolver();
		reload();
	}

	/** Re-reads all locations. Returns the number of SOULs found. */
	public int reload() {
		Map<String, Soul> loaded = new LinkedHashMap<>();

		for (String location : properties.getLocations()) {
			String pattern = location.endsWith("/") ? location + "*.md" : location + "/*.md";
			for (Resource resource : find(pattern)) {
				Soul soul = read(resource);
				Soul previous = loaded.put(soul.id(), soul);
				if (previous != null) {
					log.info("SOUL '{}' from {} overrides {}", soul.id(), soul.source(), previous.source());
				}
			}
		}

		// LinkedHashMap instead of Map.copyOf: load order determines display order in the frontend.
		this.souls = Collections.unmodifiableMap(loaded);
		if (loaded.isEmpty()) {
			log.warn("No SOUL found in {} — the built-in fallback applies", properties.getLocations());
		}
		else {
			log.info("{} SOUL(s) loaded: {}", loaded.size(), loaded.keySet());
		}
		return loaded.size();
	}

	private Resource[] find(String pattern) {
		try {
			return resolver.getResources(pattern);
		}
		catch (IOException ex) {
			// A missing external location is not an error, just empty.
			log.warn("SOUL location '{}' not readable: {}", pattern, ex.getMessage());
			return new Resource[0];
		}
	}

	private Soul read(Resource resource) {
		String source = resource.getFilename() == null ? resource.getDescription() : resource.getFilename();
		try (var in = resource.getInputStream()) {
			return SoulParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), source);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("SOUL '%s' not readable".formatted(source), ex);
		}
	}

	/** Resolution including fallback to the active SOUL. */
	public Soul resolve(String requestedId) {
		String id = (requestedId == null || requestedId.isBlank()) ? properties.getActive() : requestedId;

		Soul soul = souls.get(id);
		if (soul != null) {
			return soul;
		}
		if (souls.isEmpty()) {
			return FALLBACK;
		}
		throw new UnknownSoulException("Unknown SOUL '%s'. Available: %s".formatted(id, souls.keySet()));
	}

	public Collection<Soul> list() {
		return souls.isEmpty() ? List.of(FALLBACK) : List.copyOf(souls.values());
	}

	public String activeId() {
		return souls.containsKey(properties.getActive()) ? properties.getActive() : resolve(null).id();
	}

	/** The requested SOUL id does not exist. */
	public static class UnknownSoulException extends RuntimeException {
		public UnknownSoulException(String message) {
			super(message);
		}
	}
}
