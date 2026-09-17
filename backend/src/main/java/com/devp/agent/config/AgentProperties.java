package com.devp.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Budget eines Laufs.
 *
 * <p>Ein Agent mit Tools kann im Kreis laufen. Die Grenzen sind deshalb Pflicht, nicht
 * Komfort — und sie sind konfigurierbar, weil sie spaeter Teil der Multi-Constraint-
 * Bewertung (Latenz/Kosten/Qualitaet) werden.
 */
@ConfigurationProperties(prefix = "app.agent")
public class AgentProperties {

	/** Obergrenze fuer Tool-Aufrufe im gesamten Lauf. */
	private int maxToolCalls = 20;

	/** Obergrenze je einzelnem Tool — faengt die haeufigste Schleife ab: dasselbe Tool immer wieder. */
	private int maxCallsPerTool = 8;

	/** Wanduhr-Budget. Greift auch dann, wenn ein einzelnes Tool lange haengt. */
	private long maxDurationMs = 180_000;

	/** Wie viele Laeufe die Uebersicht hoechstens liefert. */
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
