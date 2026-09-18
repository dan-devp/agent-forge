package com.devp.agent.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origin and selection of the agent definitions.
 *
 * <p>Separate from {@link AgentProperties}, which holds the budget of a run: one is the
 * catalogue of who works, the other the limit of how long. Same shape as SOULs, tasks and
 * profiles — bundled agents ship in the jar, an external location wins for the same id.
 */
@ConfigurationProperties(prefix = "app.agents")
public class AgentCatalogProperties {

	/** Spring resource locations containing {@code *.md}; later entries override earlier ones. */
	private List<String> locations = List.of("classpath:agents/");

	/** Agent used when neither the call nor the task names one. */
	private String defaultAgent = "forge";

	public List<String> getLocations() {
		return locations;
	}

	public void setLocations(List<String> locations) {
		this.locations = locations;
	}

	public String getDefaultAgent() {
		return defaultAgent;
	}

	public void setDefaultAgent(String defaultAgent) {
		this.defaultAgent = defaultAgent;
	}
}
