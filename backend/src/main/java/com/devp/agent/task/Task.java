package com.devp.agent.task;

import java.util.List;
import java.util.Map;

/**
 * Eine wiederverwendbare, versionierte Aufgabe.
 *
 * <p>Der Unterschied zu einem getippten Auftrag: eine Aufgabe ist eine benannte Groesse
 * mit Version. Erst dadurch laesst sich spaeter sagen, welcher Lauf welche Aufgabe in
 * welcher Fassung bearbeitet hat — die Voraussetzung fuer jede Auswertung ueber mehrere
 * Laeufe hinweg.
 *
 * <p>Bewertung, Erfolgskriterien und Wiederholungen gehoeren bewusst noch nicht hierher.
 *
 * @param auftrag        der Text, der als Nutzernachricht an den Agenten geht
 * @param schwierigkeit  Stufe, vorerst nur beschreibend
 * @param soul           SOUL-Vorgabe der Aufgabe, {@code null} = Aufrufer bzw. Default entscheidet
 * @param provider       Provider-Vorgabe, {@code null} = Aufrufer bzw. Default entscheidet
 * @param maxToolCalls   Budget-Vorgabe der Aufgabe, {@code null} = globale Einstellung
 * @param maxDurationMs  Budget-Vorgabe der Aufgabe, {@code null} = globale Einstellung
 * @param abschnitte     weitere Abschnitte der Datei, unveraendert durchgereicht
 * @param quelle         Herkunftsdatei, fuer Provenienz
 */
public record Task(
		String id,
		String name,
		int version,
		String beschreibung,
		int schwierigkeit,
		List<String> tags,
		String soul,
		String provider,
		Integer maxToolCalls,
		Long maxDurationMs,
		String auftrag,
		Map<String, String> abschnitte,
		String quelle) {
}
