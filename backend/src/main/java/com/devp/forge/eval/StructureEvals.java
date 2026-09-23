package com.devp.forge.eval;

import java.util.List;
import java.util.Locale;

import com.devp.forge.eval.EvalParam.Type;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;

import org.springframework.stereotype.Component;

/**
 * Deterministic evals on shape rather than content: exact answers, length, effort.
 */
public final class StructureEvals {

	private StructureEvals() {
	}

	static int intParam(EvalConfig config, String name, int fallback) {
		String raw = config.string(name);
		if (raw == null || raw.isBlank()) {
			return fallback;
		}
		try {
			return (int) Double.parseDouble(raw);
		}
		catch (NumberFormatException ex) {
			throw new EntityInvalidException("'%s' must be a number".formatted(name));
		}
	}

	private static EvalResult noAnswer(EvalSubject subject) {
		return EvalResult.fail("Keine Antwort (Status " + subject.status() + ")");
	}

	/** One right answer. {@code contains} is too lenient when "14" must be the whole reply. */
	@Component
	public static class Equals implements Eval {
		@Override
		public String type() {
			return "equals";
		}

		@Override
		public String label() {
			return "Antwort ist genau";
		}

		@Override
		public String description() {
			return "Besteht, wenn die ganze Antwort dem Text entspricht. Leerraum an den Raendern zaehlt nicht.";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(
					EvalParam.required("text", "Erwartete Antwort", Type.STRING, "Die einzige richtige Antwort."),
					EvalParam.optional("ignore-case", "Gross-/Kleinschreibung ignorieren", Type.BOOLEAN, true,
							"Vergleich ohne Beachtung der Schreibung."),
					EvalParam.optional("collapse-whitespace", "Leerraum zusammenfassen", Type.BOOLEAN, true,
							"Mehrere Leerzeichen und Zeilenumbrueche zaehlen wie eines."));
		}

		@Override
		public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
			if (!subject.hasAnswer()) {
				return noAnswer(subject);
			}
			String expected = normalize(config.string("text"), config);
			String actual = normalize(subject.answer(), config);
			return expected.equals(actual)
					? EvalResult.pass("Antwort entspricht \"" + config.string("text") + "\"")
					: EvalResult.fail("Antwort ist \"" + shorten(subject.answer().strip()) + "\", erwartet \""
							+ config.string("text") + "\"");
		}

		private static String normalize(String text, EvalConfig config) {
			String out = text.strip();
			if (config.bool("collapse-whitespace", true)) {
				out = out.replaceAll("\\s+", " ");
			}
			if (config.bool("ignore-case", true)) {
				out = out.toLowerCase(Locale.ROOT);
			}
			return out;
		}
	}

	/** Length in characters or words, with a floor, a ceiling or both. */
	@Component
	public static class Length implements Eval {
		@Override
		public String type() {
			return "length";
		}

		@Override
		public String label() {
			return "Antwortlaenge";
		}

		@Override
		public String description() {
			return "Besteht, wenn die Antwort zwischen min und max liegt (Zeichen oder Woerter). Mindestens eins von beiden angeben.";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(
					EvalParam.optional("min", "Mindestens", Type.NUMBER, null, "Untergrenze, leer = keine."),
					EvalParam.optional("max", "Hoechstens", Type.NUMBER, null, "Obergrenze, leer = keine."),
					EvalParam.optional("unit", "Einheit", Type.STRING, "words", "words oder chars."));
		}

		@Override
		public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
			int min = intParam(config, "min", -1);
			int max = intParam(config, "max", -1);
			if (min < 0 && max < 0) {
				throw new EntityInvalidException("'length' needs 'min' or 'max'");
			}
			String unit = config.string("unit", "words").toLowerCase(Locale.ROOT);
			if (!unit.equals("words") && !unit.equals("chars")) {
				throw new EntityInvalidException("'unit' must be words or chars");
			}
			if (!subject.hasAnswer()) {
				return noAnswer(subject);
			}
			String answer = subject.answer().strip();
			int count = unit.equals("chars") ? answer.length() : answer.split("\\s+").length;
			String label = unit.equals("chars") ? "Zeichen" : "Woerter";
			if (min >= 0 && count < min) {
				return EvalResult.fail("%d %s, mindestens %d verlangt".formatted(count, label, min));
			}
			if (max >= 0 && count > max) {
				return EvalResult.fail("%d %s, hoechstens %d erlaubt".formatted(count, label, max));
			}
			return EvalResult.pass("%d %s".formatted(count, label));
		}
	}

	/** Solving is not enough; solving without flailing is the point. */
	@Component
	public static class MaxToolCalls implements Eval {
		@Override
		public String type() {
			return "max-tool-calls";
		}

		@Override
		public String label() {
			return "Hoechstens N Tool-Aufrufe";
		}

		@Override
		public String description() {
			return "Besteht, wenn der Run nicht mehr als N Tool-Aufrufe gemacht hat. Unabhaengig vom Budget des Tasks.";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(EvalParam.required("max", "Hoechstens", Type.NUMBER, "Erlaubte Aufrufe insgesamt, 0 = keiner."));
		}

		@Override
		public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
			int max = intParam(config, "max", -1);
			if (max < 0) {
				throw new EntityInvalidException("'max' must be 0 or more");
			}
			int calls = subject.calledTools().size();
			return calls <= max
					? EvalResult.pass("%d Tool-Aufruf(e), erlaubt %d".formatted(calls, max))
					: EvalResult.fail("%d Tool-Aufrufe, erlaubt %d (%s)".formatted(calls, max,
							String.join(", ", subject.calledTools())));
		}
	}

	private static String shorten(String text) {
		return text.length() <= 80 ? text : text.substring(0, 77) + "...";
	}
}
