package com.devp.agent.run;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ein Lauf des Agenten: ein Auftrag, beliebig viele Modell- und Tool-Schritte,
 * ein Ergebnis.
 *
 * <p>Traegt SOUL- und Aufgaben-Version mit, unter denen er lief — eine spaetere Bewertung
 * muss sich auf die Identitaet und die Aufgabenfassung beziehen koennen, die den Lauf
 * erzeugt haben. {@code taskId} ist {@code null}, wenn der Auftrag frei getippt wurde.
 */
public record AgentRun(
		UUID id,
		String goal,
		String taskId,
		Integer taskVersion,
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
