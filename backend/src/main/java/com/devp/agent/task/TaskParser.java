package com.devp.agent.task;

import com.devp.agent.doc.MarkdownDocument;
import com.devp.agent.doc.MarkdownParser;
import com.devp.agent.doc.MarkdownParser.MarkdownParseException;

/**
 * Liest eine Aufgabe aus Markdown mit YAML-Frontmatter — dasselbe Format wie SOULs.
 *
 * <pre>
 * ---
 * id: zeit-und-rechnung
 * name: Zeit und Rechnung
 * version: 1
 * schwierigkeit: 1
 * tags: [werkzeuge]
 * max-tool-calls: 4
 * ---
 * # Auftrag
 * Wie spaet ist es in UTC, und was ergibt 100 geteilt durch 7? Nutze die Tools.
 * </pre>
 */
public final class TaskParser {

	/** Abschnitt, dessen Text als Nutzernachricht an den Agenten geht. */
	public static final String ABSCHNITT_AUFTRAG = "Auftrag";

	private TaskParser() {
	}

	public static Task parse(String inhalt, String quelle) {
		MarkdownDocument dokument;
		try {
			dokument = MarkdownParser.parse(inhalt, quelle);

			String auftrag = dokument.abschnitt(ABSCHNITT_AUFTRAG);
			if (auftrag == null || auftrag.isBlank()) {
				// Ohne Auftrag gibt es nichts auszufuehren — das ist ein Fehler in der
				// Datei, kein leerer Lauf zur Laufzeit.
				throw new TaskParseException(quelle,
						"Abschnitt '# %s' fehlt oder ist leer".formatted(ABSCHNITT_AUFTRAG));
			}

			return new Task(
					dokument.id(),
					dokument.text("name", dokument.id()),
					dokument.ganzzahl("version", 1),
					dokument.text("beschreibung"),
					dokument.ganzzahl("schwierigkeit", 1),
					dokument.texte("tags"),
					dokument.text("soul"),
					dokument.text("provider"),
					dokument.ganzzahl("max-tool-calls"),
					dokument.ganzzahlLang("max-duration-ms"),
					auftrag,
					dokument.abschnitte(),
					quelle);
		}
		catch (MarkdownParseException ex) {
			throw new TaskParseException(quelle, ex.grund());
		}
	}

	/** Aufgabendatei ist nicht lesbar — Fehler nennt immer die Quelle. */
	public static class TaskParseException extends RuntimeException {
		public TaskParseException(String quelle, String grund) {
			super("Aufgabe '%s' konnte nicht gelesen werden: %s".formatted(quelle, grund));
		}
	}
}
