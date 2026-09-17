package com.devp.agent.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origin of the task definitions.
 *
 * <p>Same shape as {@code app.soul.locations}: the bundled tasks ship inside the jar, an
 * external location is added on top and wins for the same id. A task set can thus be swapped
 * without a rebuild.
 */
@ConfigurationProperties(prefix = "app.tasks")
public class TaskProperties {

	private List<String> locations = List.of("classpath:tasks/");

	public List<String> getLocations() {
		return locations;
	}

	public void setLocations(List<String> locations) {
		this.locations = locations;
	}
}
