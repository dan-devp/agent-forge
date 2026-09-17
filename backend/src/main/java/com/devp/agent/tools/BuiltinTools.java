package com.devp.agent.tools;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.devp.agent.config.ToolProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Built-in tools.
 *
 * <p>Deliberately side-effect free: they prove that the loop requests tools, executes them
 * and continues with the result, without a failed run being able to break anything. Real
 * capabilities arrive via MCP.
 *
 * <p>Descriptions and results are German on purpose — the model reads them, and German is
 * the agent's working language.
 */
@Component
public class BuiltinTools {

	private static final Logger log = LoggerFactory.getLogger(BuiltinTools.class);

	private final Clock clock;

	/**
	 * Fixed clock when {@code app.tools.fixed-time} is set — for reproducible runs. The only
	 * public constructor on purpose: with several constructors and no {@code @Autowired}
	 * Spring silently falls back to a no-arg one, and the fixed time would never be applied.
	 */
	public BuiltinTools(ToolProperties properties) {
		String fixedTime = properties.getFixedTime();
		if (fixedTime == null || fixedTime.isBlank()) {
			this.clock = Clock.systemDefaultZone();
		}
		else {
			this.clock = Clock.fixed(Instant.parse(fixedTime.strip()), ZoneId.of("UTC"));
			log.info("Time tool uses the fixed instant {}", fixedTime.strip());
		}
	}

	@Tool(description = "Liefert das aktuelle Datum und die aktuelle Uhrzeit. "
			+ "Nutze dieses Tool immer, wenn nach dem heutigen Datum oder der Zeit gefragt wird — "
			+ "rate sie niemals.")
	public String currentTime(
			@ToolParam(description = "Zeitzone als IANA-Id, z. B. Europe/Berlin. Leer = Europe/Berlin.",
					required = false) String timeZone) {

		ZoneId zone = timeZone == null || timeZone.isBlank() ? ZoneId.of("Europe/Berlin") : ZoneId.of(timeZone);
		String now = ZonedDateTime.now(clock.withZone(zone)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (zzz)"));
		log.info("Tool currentTime -> {}", now);
		return now;
	}

	@Tool(description = "Berechnet den Rest und das Ergebnis einer ganzzahligen Division. "
			+ "Nutze dieses Tool fuer Rechnungen, statt im Kopf zu rechnen.")
	public String divide(
			@ToolParam(description = "Dividend") long dividend,
			@ToolParam(description = "Divisor, darf nicht 0 sein") long divisor) {

		if (divisor == 0) {
			// Return the error as a result instead of throwing: the model should get the chance
			// to correct the call rather than ending the run.
			return "Fehler: Division durch 0 ist nicht definiert.";
		}
		return "%d geteilt durch %d ergibt %d Rest %d".formatted(dividend, divisor, dividend / divisor,
				dividend % divisor);
	}
}
