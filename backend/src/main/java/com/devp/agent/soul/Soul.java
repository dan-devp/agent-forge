package com.devp.agent.soul;

import java.util.List;
import java.util.Map;

/**
 * Identitaet eines KI-Mitarbeiters — eigenstaendig und versioniert, bewusst getrennt
 * vom Task-Prompt.
 *
 * <p>Eine SOUL ist mehr als Prompt-Prosa: die {@link SoulGate Gates} operationalisieren
 * die Persoenlichkeit als Pflichtfragen, die vor jeder Antwort zu beantworten sind. Prosa
 * beschreibt Haltung, Gates erzwingen sie.
 *
 * @param abschnitte Ueberschrift -&gt; Text, in Dateireihenfolge. Bewusst nicht typisiert:
 *                   welche Abschnitte eine SOUL fuehrt, entscheidet die SOUL.
 * @param quelle     Herkunft der Definition, fuer Provenienz in Antworten und Logs.
 */
public record Soul(
		String id,
		String name,
		String rolle,
		int version,
		String beschreibung,
		List<SoulGate> gates,
		Map<String, String> abschnitte,
		String quelle) {

	/** Anzeigename mit Rolle, z. B. fuer Logs und das Frontend. */
	public String bezeichnung() {
		return rolle == null || rolle.isBlank() ? name : "%s (%s)".formatted(name, rolle);
	}
}
