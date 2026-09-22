package com.devp.forge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Switches that apply to every model regardless of its file. */
@ConfigurationProperties(prefix = "app.models")
public class ModelProperties {

	/**
	 * When set, every model's base URL is replaced by this one. The test profile points it at
	 * a dead address so no automated test can reach a real endpoint, whatever the files say.
	 */
	private String baseUrlOverride;

	public String getBaseUrlOverride() {
		return baseUrlOverride;
	}

	public void setBaseUrlOverride(String baseUrlOverride) {
		this.baseUrlOverride = baseUrlOverride;
	}

	public boolean hasOverride() {
		return baseUrlOverride != null && !baseUrlOverride.isBlank();
	}
}
