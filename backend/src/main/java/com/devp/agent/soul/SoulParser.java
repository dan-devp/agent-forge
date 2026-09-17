package com.devp.agent.soul;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.devp.agent.doc.MarkdownDocument;
import com.devp.agent.doc.MarkdownParser;
import com.devp.agent.doc.MarkdownParser.MarkdownParseException;

/**
 * Liest eine SOUL aus Markdown mit YAML-Frontmatter.
 *
 * <p>Das Dateiformat kommt aus {@link MarkdownParser} und ist mit Aufgaben (und spaeter
 * Skills) geteilt. Hier steht nur, was eine SOUL daraus macht — insbesondere die Gates.
 *
 * <pre>
 * ---
 * id: forge
 * version: 1
 * gates:
 *   - id: beleg
 *     frage: ...
 *     blockiert: true
 * ---
 * # Identitaet
 * ...
 * </pre>
 */
public final class SoulParser {

	private SoulParser() {
	}

	/**
	 * @param inhalt kompletter Dateiinhalt
	 * @param quelle Herkunftsbezeichnung (Pfad), auch Fallback fuer eine fehlende Id
	 */
	public static Soul parse(String inhalt, String quelle) {
		MarkdownDocument dokument;
		int version;
		try {
			dokument = MarkdownParser.parse(inhalt, quelle);
			version = dokument.ganzzahl("version", 1);
		}
		catch (MarkdownParseException ex) {
			throw new SoulParseException(quelle, ex.grund());
		}

		return new Soul(
				dokument.id(),
				dokument.text("name", dokument.id()),
				dokument.text("rolle"),
				version,
				dokument.text("beschreibung"),
				gates(dokument),
				dokument.abschnitte(),
				quelle);
	}

	/**
	 * Gates sind der SOUL-spezifische Teil: Pflichtfragen mit der Angabe, ob eine negative
	 * Antwort das Liefern verbietet.
	 */
	private static List<SoulGate> gates(MarkdownDocument dokument) {
		List<Map<?, ?>> roh;
		try {
			roh = dokument.objekte("gates");
		}
		catch (MarkdownParseException ex) {
			throw new SoulParseException(dokument.quelle(), ex.grund());
		}

		List<SoulGate> gates = new ArrayList<>();
		for (Map<?, ?> eintrag : roh) {
			String id = text(eintrag.get("id"));
			String frage = text(eintrag.get("frage"));
			if (id == null || id.isBlank() || frage == null || frage.isBlank()) {
				throw new SoulParseException(dokument.quelle(), "Gate ohne 'id' oder 'frage': " + eintrag);
			}
			gates.add(new SoulGate(id, frage, Boolean.TRUE.equals(eintrag.get("blockiert"))));
		}
		return List.copyOf(gates);
	}

	private static String text(Object wert) {
		return wert == null ? null : String.valueOf(wert).strip();
	}

	/** SOUL-Datei ist nicht lesbar — Fehler nennt immer die Quelle. */
	public static class SoulParseException extends RuntimeException {
		public SoulParseException(String quelle, String grund) {
			super("SOUL '%s' konnte nicht gelesen werden: %s".formatted(quelle, grund));
		}
	}
}
