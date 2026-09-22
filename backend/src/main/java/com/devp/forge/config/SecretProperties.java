package com.devp.forge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API keys never touch a file the UI writes. A model file names an environment variable;
 * the value stays in the environment. The UI only ever sees the names, and only those with
 * this prefix — listing every variable of the process would leak more than it helps.
 */
@ConfigurationProperties(prefix = "app.secrets")
public class SecretProperties {

	private String envPrefix = "API_KEY_";

	public String getEnvPrefix() {
		return envPrefix;
	}

	public void setEnvPrefix(String envPrefix) {
		this.envPrefix = envPrefix;
	}
}
