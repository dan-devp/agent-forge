package com.devp.agent.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Herkunft der Aufgabendefinitionen.
 *
 * <p>Gleiche Form wie {@code app.soul.locations}: die mitgelieferten Aufgaben stecken im
 * Jar, eine externe Location kommt dazu und gewinnt bei gleicher Id. Damit laesst sich ein
 * Aufgabensatz austauschen, ohne neu zu bauen.
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
