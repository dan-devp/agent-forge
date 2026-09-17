package com.devp.agent.doc;

import java.util.List;
import java.util.Map;

/**
 * Eine geparste Markdown-Datei mit YAML-Frontmatter.
 *
 * <p>Gemeinsames Format aller autorierten Entitaeten des Frameworks — SOUL, Aufgabe und
 * spaeter Skill. Bewusst nah an agentskills.io: maschinenlesbare Metadaten oben, Prosa im
 * Body. Die Historie liegt damit im Git statt in einer Tabelle.
 *
 * @param kopf       Frontmatter, Reihenfolge wie in der Datei
 * @param abschnitte {@code # Ueberschrift} -&gt; Text, Reihenfolge wie in der Datei
 * @param quelle     Herkunft der Definition, fuer Provenienz in Logs und Fehlern
 */
public record MarkdownDocument(String id, Map<String, Object> kopf, Map<String, String> abschnitte, String quelle) {

	/** Frontmatter-Wert als Text, {@code null} wenn nicht gesetzt oder leer. */
	public String text(String schluessel) {
		Object wert = kopf.get(schluessel);
		if (wert == null) {
			return null;
		}
		String text = String.valueOf(wert).strip();
		return text.isEmpty() ? null : text;
	}

	public String text(String schluessel, String ersatz) {
		String wert = text(schluessel);
		return wert == null ? ersatz : wert;
	}

	/** Ganzzahl aus dem Frontmatter, {@code null} wenn nicht gesetzt. */
	public Integer ganzzahl(String schluessel) {
		Object wert = kopf.get(schluessel);
		if (wert == null) {
			return null;
		}
		if (wert instanceof Number zahl) {
			return zahl.intValue();
		}
		try {
			return Integer.parseInt(String.valueOf(wert).trim());
		}
		catch (NumberFormatException ex) {
			throw new MarkdownParser.MarkdownParseException(quelle, schluessel + " muss eine Zahl sein, war: " + wert);
		}
	}

	public int ganzzahl(String schluessel, int ersatz) {
		Integer wert = ganzzahl(schluessel);
		return wert == null ? ersatz : wert;
	}

	public Long ganzzahlLang(String schluessel) {
		Integer wert = ganzzahl(schluessel);
		return wert == null ? null : wert.longValue();
	}

	/** Liste von Texten aus dem Frontmatter; ein einzelner Wert gilt als einelementige Liste. */
	public List<String> texte(String schluessel) {
		Object wert = kopf.get(schluessel);
		if (wert == null) {
			return List.of();
		}
		if (wert instanceof List<?> liste) {
			return liste.stream().map(e -> String.valueOf(e).strip()).filter(e -> !e.isEmpty()).toList();
		}
		String einzeln = String.valueOf(wert).strip();
		return einzeln.isEmpty() ? List.of() : List.of(einzeln);
	}

	/** Liste von Objekten aus dem Frontmatter, z. B. die Gates einer SOUL. */
	public List<Map<?, ?>> objekte(String schluessel) {
		Object wert = kopf.get(schluessel);
		if (wert == null) {
			return List.of();
		}
		if (!(wert instanceof List<?> liste)) {
			throw new MarkdownParser.MarkdownParseException(quelle, schluessel + " muss eine Liste sein");
		}
		List<Map<?, ?>> objekte = new java.util.ArrayList<>();
		for (Object eintrag : liste) {
			if (!(eintrag instanceof Map<?, ?> karte)) {
				throw new MarkdownParser.MarkdownParseException(quelle,
						"jeder Eintrag unter '%s' muss ein Objekt sein — gefunden: %s".formatted(schluessel, eintrag));
			}
			objekte.add(karte);
		}
		return List.copyOf(objekte);
	}

	/** Abschnitt aus dem Body, {@code null} wenn nicht vorhanden. */
	public String abschnitt(String ueberschrift) {
		return abschnitte.get(ueberschrift);
	}
}
