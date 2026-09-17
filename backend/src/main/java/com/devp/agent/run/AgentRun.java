package com.devp.agent.run;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ein Lauf des Agenten: ein Auftrag, beliebig viele Modell- und Tool-Schritte,
 * ein Ergebnis.
 *
 * <p>Traegt die SOUL-Version mit, unter der er lief — eine spaetere Bewertung muss
 * sich auf die Identitaet beziehen koennen, die den Lauf gefuehrt hat.
 */
public record AgentRun(
		UUID id,
		String goal,
		String soulId,
		int soulVersion,
		String providerId,
		String model,
		RunStatus status,
		String answer,
		String error,
		int steps,
		Integer totalTokens,
		Long durationMs,
		Instant startedAt,
		Instant finishedAt,
		List<RunStep> trace) {
}
