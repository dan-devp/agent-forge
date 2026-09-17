package com.devp.agent.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origin and selection of the SOUL definitions.
 *
 * <p>The default SOULs ship inside the jar; an external location is added on top and wins
 * for the same id. A SOUL is thus replaceable in operation without a rebuild.
 */
@ConfigurationProperties(prefix = "app.soul")
public class SoulProperties {

	/** Spring resource locations containing {@code *.md}; later entries override earlier ones. */
	private List<String> locations = List.of("classpath:souls/");

	/** SOUL used when a call names none. */
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
