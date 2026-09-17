package com.devp.agent.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origin and selection of the strategy profiles.
 *
 * <p>Same shape as SOULs and tasks: bundled profiles ship in the jar, an external location
 * wins for the same id. The default profile is {@code baseline} — no strategies — so every
 * run without an explicit profile is a baseline run and can serve as a reference.
 */
@ConfigurationProperties(prefix = "app.profiles")
public class ProfileProperties {

	private List<String> locations = List.of("classpath:profiles/");

	/** Profile used when neither the call nor the task names one. */
	private String defaultProfile = "baseline";

	public List<String> getLocations() {
		return locations;
	}

	public void setLocations(List<String> locations) {
		this.locations = locations;
	}

	public String getDefaultProfile() {
		return defaultProfile;
	}

	public void setDefaultProfile(String defaultProfile) {
		this.defaultProfile = defaultProfile;
	}
}
