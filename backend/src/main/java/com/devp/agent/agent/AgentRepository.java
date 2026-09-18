package com.devp.agent.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.devp.agent.agent.Agent.ToolAccess;
import com.devp.agent.config.AgentCatalogProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Loads all agents from the configured locations and keeps them in memory.
 *
 * <p>Same pattern as SOULs, tasks and profiles: the history belongs in Git, the catalogue in
 * memory, an external location wins for the same id so an agent is replaceable in operation
 * without a rebuild.
 */
@Component
public class AgentRepository {

	private static final Logger log = LoggerFactory.getLogger(AgentRepository.class);

	/**
	 * Applies when no agent file was found: whatever the call and the task say, with every
	 * tool. That is exactly the behaviour before agents existed — an empty catalogue must not
	 * stop the application.
	 */
	private static final Agent FALLBACK = new Agent("default", "Default", 0,
			"Built-in fallback because no agent file was found.", null, null, ToolAccess.ALL, Map.of(), "built-in");

	private final AgentCatalogProperties properties;
	private final ResourcePatternResolver resolver;

	private volatile Map<String, Agent> agents = Map.of();

	public AgentRepository(AgentCatalogProperties properties) {
		this.properties = properties;
		this.resolver = new PathMatchingResourcePatternResolver();
		reload();
	}

	/** Re-reads all locations. Returns the number of agents found. */
	public int reload() {
		Map<String, Agent> loaded = new LinkedHashMap<>();

		for (String location : properties.getLocations()) {
			String pattern = location.endsWith("/") ? location + "*.md" : location + "/*.md";
			for (Resource resource : find(pattern)) {
				Agent agent = read(resource);
				Agent previous = loaded.put(agent.id(), agent);
				if (previous != null) {
					log.info("Agent '{}' from {} overrides {}", agent.id(), agent.source(), previous.source());
				}
			}
		}

		// Map.copyOf loses the order — the load order decides which agent the UI offers first.
		this.agents = Collections.unmodifiableMap(loaded);
		if (loaded.isEmpty()) {
			log.warn("No agent found in {} — the built-in fallback applies", properties.getLocations());
		}
		else {
			log.info("{} agent(s) loaded: {}", loaded.size(), loaded.keySet());
		}
		return loaded.size();
	}

	private Resource[] find(String pattern) {
		try {
			return resolver.getResources(pattern);
		}
		catch (IOException ex) {
			log.warn("Agent location '{}' not readable: {}", pattern, ex.getMessage());
			return new Resource[0];
		}
	}

	private Agent read(Resource resource) {
		String source = resource.getFilename() == null ? resource.getDescription() : resource.getFilename();
		try (var in = resource.getInputStream()) {
			return AgentParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), source);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Agent '%s' not readable".formatted(source), ex);
		}
	}

	/** Resolution including fallback to the default agent. */
	public Agent resolve(String requestedId) {
		String id = (requestedId == null || requestedId.isBlank()) ? properties.getDefaultAgent() : requestedId;

		Agent agent = agents.get(id);
		if (agent != null) {
			return agent;
		}
		if (agents.isEmpty()) {
			return FALLBACK;
		}
		throw new UnknownAgentException("Unknown agent '%s'. Available: %s".formatted(id, agents.keySet()));
	}

	public Collection<Agent> list() {
		return agents.isEmpty() ? List.of(FALLBACK) : List.copyOf(agents.values());
	}

	public String defaultId() {
		return agents.containsKey(properties.getDefaultAgent()) ? properties.getDefaultAgent() : resolve(null).id();
	}

	/** The requested agent id does not exist. */
	public static class UnknownAgentException extends RuntimeException {

		public UnknownAgentException(String message) {
			super(message);
		}
	}
}
