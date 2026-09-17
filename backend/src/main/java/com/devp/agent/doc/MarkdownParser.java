package com.devp.agent.doc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Zerlegt Markdown mit YAML-Frontmatter in {@link MarkdownDocument}.
 *
 * <pre>
 * ---
 * id: forge
 * version: 1
 * ---
 * # Identitaet
 * ...
 * </pre>
 *
 * <p>Kennt nur das Dateiformat, nicht die Bedeutung der Felder — welche Felder Pflicht
 * sind, entscheidet die jeweilige Entitaet (SOUL, Aufgabe, spaeter Skill).
 */
public final class MarkdownParser {

	private static final String TRENNER = "---";

	private MarkdownParser() {
	}

	/**
	 * @param quelle Herkunftsbezeichnung (Pfad), auch Fallback fuer eine fehlende Id
	 */
	public static MarkdownDocument parse(String inhalt, String quelle) {
		if (inhalt == null || inhalt.isBlank()) {
			throw new MarkdownParseException(quelle, "Datei ist leer");
		}

		Geteilt geteilt = trenneFrontmatter(inhalt, quelle);
		Map<String, Object> kopf = ladeYaml(geteilt.frontmatter(), quelle);

		Object roheId = kopf.get("id");
		String id = roheId == null ? null : String.valueOf(roheId).strip();
		if (id == null || id.isEmpty()) {
			id = dateiBasisName(quelle);
		}

		return new MarkdownDocument(id, kopf, abschnitte(geteilt.body()), quelle);
	}

	/**
	 * Frontmatter muss in der ersten Zeile beginnen — sonst laesst sich nicht
	 * unterscheiden, ob eine Datei Metadaten hat oder nur eine Trennlinie enthaelt.
	 */
	private static Geteilt trenneFrontmatter(String inhalt, String quelle) {
		String normalisiert = inhalt.replace("\r\n", "\n").stripLeading();
		if (!normalisiert.startsWith(TRENNER)) {
			throw new MarkdownParseException(quelle, "kein YAML-Frontmatter — Datei muss mit '---' beginnen");
		}

		int start = normalisiert.indexOf('\n');
		if (start < 0) {
			throw new MarkdownParseException(quelle, "Frontmatter ist nicht geschlossen");
		}

		int ende = normalisiert.indexOf("\n" + TRENNER, start);
		if (ende < 0) {
			throw new MarkdownParseException(quelle, "Frontmatter ist nicht geschlossen (zweites '---' fehlt)");
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
				throw new MarkdownParseException(quelle, "Frontmatter ist leer");
			}
			if (!(geladen instanceof Map<?, ?> karte)) {
				throw new MarkdownParseException(quelle, "Frontmatter muss ein YAML-Objekt sein");
			}
			Map<String, Object> ergebnis = new LinkedHashMap<>();
			karte.forEach((k, v) -> ergebnis.put(String.valueOf(k), v));
			return Collections.unmodifiableMap(ergebnis);
		}
		catch (YAMLException ex) {
			throw new MarkdownParseException(quelle, "Frontmatter ist kein gueltiges YAML: " + ex.getMessage());
		}
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
		// Map.copyOf verliert die Reihenfolge — die Abschnittsfolge ist Teil der Definition.
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

	private record Geteilt(String frontmatter, String body) {
	}

	/** Datei ist nicht lesbar — die Meldung nennt immer die Quelle. */
	public static class MarkdownParseException extends RuntimeException {

		private final String grund;

		public MarkdownParseException(String quelle, String grund) {
			super("'%s' konnte nicht gelesen werden: %s".formatted(quelle, grund));
			this.grund = grund;
		}

		/** Der Grund ohne Quellenangabe — damit eine Entitaet ihn in ihre eigene Meldung setzen kann. */
		public String grund() {
			return grund;
		}
	}
}
