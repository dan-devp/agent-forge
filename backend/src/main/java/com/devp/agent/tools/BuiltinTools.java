package com.devp.agent.tools;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Eingebaute Tools.
 *
 * <p>Absichtlich wirkungsfrei: sie beweisen, dass die Schleife Tools anfordert,
 * ausfuehrt und mit dem Ergebnis weiterarbeitet, ohne dass ein Fehllauf etwas
 * kaputt machen kann. Echte Faehigkeiten kommen ueber MCP dazu.
 */
@Component
public class BuiltinTools {

	private static final Logger log = LoggerFactory.getLogger(BuiltinTools.class);

	@Tool(description = "Liefert das aktuelle Datum und die aktuelle Uhrzeit. "
			+ "Nutze dieses Tool immer, wenn nach dem heutigen Datum oder der Zeit gefragt wird — "
			+ "rate sie niemals.")
	public String aktuelleZeit(
			@ToolParam(description = "Zeitzone als IANA-Id, z. B. Europe/Berlin. Leer = Europe/Berlin.",
					required = false) String zeitzone) {

		ZoneId zone = zeitzone == null || zeitzone.isBlank() ? ZoneId.of("Europe/Berlin") : ZoneId.of(zeitzone);
		String jetzt = ZonedDateTime.now(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (zzz)"));
		log.info("Tool aktuelleZeit -> {}", jetzt);
		return jetzt;
	}

	@Tool(description = "Berechnet den Rest und das Ergebnis einer ganzzahligen Division. "
			+ "Nutze dieses Tool fuer Rechnungen, statt im Kopf zu rechnen.")
	public String teilen(
			@ToolParam(description = "Dividend") long dividend,
			@ToolParam(description = "Divisor, darf nicht 0 sein") long divisor) {

		if (divisor == 0) {
			// Fehler als Ergebnis zurueckgeben, nicht werfen: das Modell soll die Chance
			// bekommen, den Aufruf zu korrigieren, statt den Lauf zu beenden.
			return "Fehler: Division durch 0 ist nicht definiert.";
		}
		return "%d geteilt durch %d ergibt %d Rest %d".formatted(dividend, divisor, dividend / divisor,
				dividend % divisor);
	}
}
