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

	/**
	 * Global cap on rejections per strategy in the beforeFinish loop, regardless of what a
	 * profile asks for. A misconfigured profile must not loop until the time budget hits.
	 */
	private int maxVerifyRounds = 3;

	/** Upper bound for repetitions in one series — a typo must not schedule a thousand model calls. */
	private int maxSeriesRepetitions = 50;

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

	public int getMaxVerifyRounds() {
		return maxVerifyRounds;
	}

	public void setMaxVerifyRounds(int maxVerifyRounds) {
		this.maxVerifyRounds = maxVerifyRounds;
	}

	public void setHistoryLimit(int historyLimit) {
		this.historyLimit = historyLimit;
	}

	public int getMaxSeriesRepetitions() {
		return maxSeriesRepetitions;
	}

	public void setMaxSeriesRepetitions(int maxSeriesRepetitions) {
		this.maxSeriesRepetitions = maxSeriesRepetitions;
	}
}
