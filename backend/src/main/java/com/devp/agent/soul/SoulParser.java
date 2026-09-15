package com.devp.agent.soul;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Liest eine SOUL aus Markdown mit YAML-Frontmatter.
 *
 * <p>Format bewusst nah an agentskills.io: maschinenlesbare Metadaten oben, Prosa im Body.
 * Damit koennen Skills (Schritt 3) spaeter denselben Parser nutzen.
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

	private static final String TRENNER = "---";

	private SoulParser() {
	}

	/**
	 * @param inhalt     kompletter Dateiinhalt
	 * @param quelle     Herkunftsbezeichnung (Pfad), auch Fallback fuer eine fehlende Id
	 */
	public static Soul parse(String inhalt, String quelle) {
		if (inhalt == null || inhalt.isBlank()) {
			throw new SoulParseException(quelle, "Datei ist leer");
		}

		Geteilt geteilt = trenneFrontmatter(inhalt, quelle);
		Map<String, Object> kopf = ladeYaml(geteilt.frontmatter(), quelle);

		String id = text(kopf.get("id"));
		if (id == null || id.isBlank()) {
			id = dateiBasisName(quelle);
		}

		return new Soul(
				id,
				kopf.get("name") == null ? id : text(kopf.get("name")),
				text(kopf.get("rolle")),
				version(kopf.get("version"), quelle),
				text(kopf.get("beschreibung")),
				gates(kopf.get("gates"), quelle),
				abschnitte(geteilt.body()),
				quelle);
	}

	/**
	 * Frontmatter muss in der ersten Zeile beginnen — sonst laesst sich nicht
	 * unterscheiden, ob eine Datei Metadaten hat oder nur eine Trennlinie enthaelt.
	 */
	private static Geteilt trenneFrontmatter(String inhalt, String quelle) {
		String normalisiert = inhalt.replace("\r\n", "\n").stripLeading();
		if (!normalisiert.startsWith(TRENNER)) {
			throw new SoulParseException(quelle, "kein YAML-Frontmatter — Datei muss mit '---' beginnen");
		}

		int start = normalisiert.indexOf('\n');
		if (start < 0) {
			throw new SoulParseException(quelle, "Frontmatter ist nicht geschlossen");
		}

		int ende = normalisiert.indexOf("\n" + TRENNER, start);
		if (ende < 0) {
			throw new SoulParseException(quelle, "Frontmatter ist nicht geschlossen (zweites '---' fehlt)");
		}

		String frontmatter = normalisiert.substring(start + 1, ende);
		int bodyStart = normalisiert.indexOf('\n', ende + 1);
		String body = bodyStart < 0 ? "" : normalisiert.substring(bodyStart + 1);
		return new Geteilt(frontmatter, body);
	}

	private static Map<String, Object> ladeYaml(String frontmatter, String quelle) {
		try {
			Object geladen = new Yaml().load(frontmatter);
			if (geladen == null) {
				throw new SoulParseException(quelle, "Frontmatter ist leer");
			}
			if (!(geladen instanceof Map<?, ?> karte)) {
				throw new SoulParseException(quelle, "Frontmatter muss ein YAML-Objekt sein");
			}
			Map<String, Object> ergebnis = new LinkedHashMap<>();
			karte.forEach((k, v) -> ergebnis.put(String.valueOf(k), v));
			return ergebnis;
		}
		catch (YAMLException ex) {
			throw new SoulParseException(quelle, "Frontmatter ist kein gueltiges YAML: " + ex.getMessage());
		}
	}

	private static int version(Object roh, String quelle) {
		if (roh == null) {
			return 1;
		}
		if (roh instanceof Number zahl) {
			return zahl.intValue();
		}
		try {
			return Integer.parseInt(String.valueOf(roh).trim());
		}
		catch (NumberFormatException ex) {
			throw new SoulParseException(quelle, "version muss eine Zahl sein, war: " + roh);
		}
	}

	private static List<SoulGate> gates(Object roh, String quelle) {
		if (roh == null) {
			return List.of();
		}
		if (!(roh instanceof List<?> liste)) {
			throw new SoulParseException(quelle, "gates muss eine Liste sein");
		}

		List<SoulGate> gates = new ArrayList<>();
		for (Object eintrag : liste) {
			if (!(eintrag instanceof Map<?, ?> karte)) {
				throw new SoulParseException(quelle,
						"jedes Gate braucht 'id' und 'frage' — gefunden: " + eintrag);
			}
			String id = text(karte.get("id"));
			String frage = text(karte.get("frage"));
			if (id == null || id.isBlank() || frage == null || frage.isBlank()) {
				throw new SoulParseException(quelle, "Gate ohne 'id' oder 'frage': " + karte);
			}
			gates.add(new SoulGate(id, frage, Boolean.TRUE.equals(karte.get("blockiert"))));
		}
		return List.copyOf(gates);
	}

	/** Body in Abschnitte nach {@code # Ueberschrift} zerlegen, Reihenfolge bleibt erhalten. */
	private static Map<String, String> abschnitte(String body) {
		Map<String, String> abschnitte = new LinkedHashMap<>();
		String ueberschrift = null;
		StringBuilder puffer = new StringBuilder();

		for (String zeile : body.split("\n", -1)) {
			if (zeile.startsWith("# ")) {
				ablegen(abschnitte, ueberschrift, puffer);
				ueberschrift = zeile.substring(2).trim();
				puffer.setLength(0);
			}
			else {
				puffer.append(zeile).append('\n');
			}
		}
		ablegen(abschnitte, ueberschrift, puffer);
		// Map.copyOf verliert die Reihenfolge — die Abschnittsfolge ist hier aber Teil der SOUL.
		return Collections.unmodifiableMap(abschnitte);
	}

	private static void ablegen(Map<String, String> ziel, String ueberschrift, StringBuilder puffer) {
		if (ueberschrift == null) {
			return;
		}
		String text = puffer.toString().strip();
		if (!text.isEmpty()) {
			ziel.put(ueberschrift, text);
		}
	}

	private static String dateiBasisName(String quelle) {
		// Quelle kann ein Classpath-Pfad oder ein Windows-Dateipfad sein.
		int schnitt = Math.max(quelle.lastIndexOf('/'), quelle.lastIndexOf('\\'));
		String name = quelle.substring(schnitt + 1);
		return name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
	}

	private static String text(Object wert) {
		return wert == null ? null : String.valueOf(wert).strip();
	}

	private record Geteilt(String frontmatter, String body) {
	}

	/** SOUL-Datei ist nicht lesbar — Fehler nennt immer die Quelle. */
	public static class SoulParseException extends RuntimeException {
		public SoulParseException(String quelle, String grund) {
			super("SOUL '%s' konnte nicht gelesen werden: %s".formatted(quelle, grund));
		}
	}
}
