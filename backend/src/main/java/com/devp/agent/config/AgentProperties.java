package com.devp.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Budget of a run.
 *
 * <p>An agent with tools can loop forever. The limits are therefore mandatory, not a
 * convenience — and configurable because they later become part of the multi-constraint
 * evaluation (latency/cost/quality).
 */
@ConfigurationProperties(prefix = "app.agent")
public class AgentProperties {

	/** Upper bound for tool calls in the whole run. */
	private int maxToolCalls = 20;

	/** Upper bound per single tool — catches the most common loop: the same tool over and over. */
	private int maxCallsPerTool = 8;

	/** Wall-clock budget. Also applies when a single tool hangs for a long time. */
	private long maxDurationMs = 180_000;

	/** How many runs the history lists at most. */
	private int historyLimit = 50;

	public int getMaxToolCalls() {
		return maxToolCalls;
	}

	public void setMaxToolCalls(int maxToolCalls) {
		this.maxToolCalls = maxToolCalls;
	}

	public int getMaxCallsPerTool() {
		return maxCallsPerTool;
	}

	public void setMaxCallsPerTool(int maxCallsPerTool) {
		this.maxCallsPerTool = maxCallsPerTool;
	}

	public long getMaxDurationMs() {
		return maxDurationMs;
	}

	public void setMaxDurationMs(long maxDurationMs) {
		this.maxDurationMs = maxDurationMs;
	}

	public int getHistoryLimit() {
		return historyLimit;
	}

	public void setHistoryLimit(int historyLimit) {
		this.historyLimit = historyLimit;
	}
}
