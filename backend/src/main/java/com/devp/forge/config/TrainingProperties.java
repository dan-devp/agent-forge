package com.devp.forge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** How a training session waits and how much of its history the API lists. */
@ConfigurationProperties(prefix = "app.training")
public class TrainingProperties {

	/** How often a session looks at its batch or at a pending decision. */
	private long pollMs = 500;

	/** Sessions per training in a listing. */
	private int historyLimit = 50;

	public long getPollMs() {
		return pollMs;
	}

	public void setPollMs(long pollMs) {
		this.pollMs = pollMs;
	}

	public int getHistoryLimit() {
		return historyLimit;
	}

	public void setHistoryLimit(int historyLimit) {
		this.historyLimit = historyLimit;
	}
}
