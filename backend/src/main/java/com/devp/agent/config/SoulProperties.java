package com.devp.agent.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Herkunft und Auswahl der SOUL-Definitionen.
 *
 * <p>Die Standard-SOULs liegen im Jar; eine externe Location kommt zusaetzlich dazu und
 * gewinnt bei gleicher Id. Damit ist eine SOUL im Betrieb austauschbar, ohne neu zu bauen.
 */
@ConfigurationProperties(prefix = "app.soul")
public class SoulProperties {

	/** Spring-Resource-Locations mit {@code *.md}; spaetere Eintraege ueberschreiben fruehere. */
	private List<String> locations = List.of("classpath:souls/");

	/** SOUL, die genutzt wird, wenn ein Aufruf keine angibt. */
	private String active = "forge";

	public List<String> getLocations() {
		return locations;
	}

	public void setLocations(List<String> locations) {
		this.locations = locations;
	}

	public String getActive() {
		return active;
	}

	public void setActive(String active) {
		this.active = active;
	}
}
