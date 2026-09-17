package com.devp.agent.run;

import java.time.Instant;

/**
 * Ein Schritt im Trace. Append-only: einmal geschrieben, nie geaendert.
 *
 * @param nr         fortlaufend ab 1 innerhalb des Laufs
 * @param name       Tool-Name bzw. Modell-Id
 * @param input      Tool-Argumente als JSON bzw. die Anfrage an das Modell
 * @param output     Tool-Ergebnis bzw. Modellantwort
 */
public record RunStep(
		int nr,
		StepType type,
		String name,
		String input,
		String output,
		Integer tokens,
		Long durationMs,
		String error,
		Instant createdAt) {
}
