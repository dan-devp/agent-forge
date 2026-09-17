package com.devp.agent.profile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.devp.agent.config.ProfileProperties;
import com.devp.agent.profile.Profile.StrategyConfig;
import com.devp.agent.profile.ProfileParser.ProfileParseException;
import com.devp.agent.strategy.StrategyRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Loads all strategy profiles from the configured locations and keeps them in memory.
 *
 * <p>Same pattern as SOULs and tasks. In addition every strategy id is checked against the
 * {@link StrategyRegistry} on load: a profile naming an unknown strategy is a defect in the
 * file and must surface here, not as a silently weaker run.
 */
@Component
public class ProfileRepository {

	private static final Logger log = LoggerFactory.getLogger(ProfileRepository.class);

	/** Applies when no profile file was found: no strategies at all. */
	private static final Profile EMPTY = new Profile("baseline", "Baseline", 0,
			"Built-in fallback because no profile file was found.", List.of(), Map.of(), "built-in");

	private final ProfileProperties properties;
	private final StrategyRegistry strategies;
	private final ResourcePatternResolver resolver;

	private volatile Map<String, Profile> profiles = Map.of();

	public ProfileRepository(ProfileProperties properties, StrategyRegistry strategies) {
		this.properties = properties;
		this.strategies = strategies;
		this.resolver = new PathMatchingResourcePatternResolver();
		reload();
	}

	/** Re-reads all locations. Returns the number of profiles found. */
	public int reload() {
		Map<String, Profile> loaded = new LinkedHashMap<>();

		for (String location : properties.getLocations()) {
			String pattern = location.endsWith("/") ? location + "*.md" : location + "/*.md";
			for (Resource resource : find(pattern)) {
				Profile profile = read(resource);
				verify(profile);
				Profile previous = loaded.put(profile.id(), profile);
				if (previous != null) {
					log.info("Profile '{}' from {} overrides {}", profile.id(), profile.source(), previous.source());
				}
			}
		}

		this.profiles = Collections.unmodifiableMap(loaded);
		if (loaded.isEmpty()) {
			log.warn("No profile found in {} — the built-in empty baseline applies", properties.getLocations());
		}
		else {
			log.info("{} profile(s) loaded: {}", loaded.size(), loaded.keySet());
		}
		return loaded.size();
	}

	private void verify(Profile profile) {
		for (StrategyConfig config : profile.strategies()) {
			if (!strategies.knows(config.id())) {
				throw new ProfileParseException(profile.source(),
						"unknown strategy '%s' — registered: %s".formatted(config.id(), strategies.ids()));
			}
		}
	}

	private Resource[] find(String pattern) {
		try {
			return resolver.getResources(pattern);
		}
		catch (IOException ex) {
			log.warn("Profile location '{}' not readable: {}", pattern, ex.getMessage());
			return new Resource[0];
		}
	}

	private Profile read(Resource resource) {
		String source = resource.getFilename() == null ? resource.getDescription() : resource.getFilename();
		try (var in = resource.getInputStream()) {
			return ProfileParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), source);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Profile '%s' not readable".formatted(source), ex);
		}
	}

	/** Resolution including fallback to the default profile. */
	public Profile resolve(String requestedId) {
		String id = (requestedId == null || requestedId.isBlank()) ? properties.getDefaultProfile() : requestedId;

		Profile profile = profiles.get(id);
		if (profile != null) {
			return profile;
		}
		if (profiles.isEmpty()) {
			return EMPTY;
		}
		throw new UnknownProfileException("Unknown profile '%s'. Available: %s".formatted(id, profiles.keySet()));
	}

	public Collection<Profile> list() {
		return profiles.isEmpty() ? List.of(EMPTY) : List.copyOf(profiles.values());
	}

	public String defaultId() {
		return profiles.containsKey(properties.getDefaultProfile()) ? properties.getDefaultProfile() : resolve(null).id();
	}

	/** The requested profile id does not exist. */
	public static class UnknownProfileException extends RuntimeException {
		public UnknownProfileException(String message) {
			super(message);
		}
	}
}
