package com.devp.forge.eval;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.devp.forge.eval.EvalParam.Type;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;

import org.springframework.stereotype.Component;

/**
 * The deterministic evals on the answer text. Free, instant, incorruptible — the first line
 * of every task.
 *
 * <p>A run without an answer fails all of them, the negative ones included: a run that
 * delivered nothing did nothing right.
 */
public final class TextEvals {

	private TextEvals() {
	}

	private static final EvalParam CASE = EvalParam.optional("ignore-case", "Gro\u00df-/Kleinschreibung ignorieren",
			Type.BOOLEAN, true, "Vergleich ohne Beachtung der Schreibung.");

	private static boolean contains(EvalConfig config, EvalSubject subject) {
		String answer = subject.answer();
		String text = config.string("text");
		if (config.bool("ignore-case", true)) {
			return answer.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
		}
		return answer.contains(text);
	}

	private static Pattern pattern(EvalConfig config) {
		String regex = config.string("pattern");
		try {
			return config.bool("ignore-case", false)
					? Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
					: Pattern.compile(regex);
		}
		catch (PatternSyntaxException ex) {
			throw new EntityInvalidException("invalid regular expression '%s': %s".formatted(regex, ex.getDescription()));
		}
	}

	private static EvalResult noAnswer(EvalSubject subject) {
		return EvalResult.fail("Keine Antwort (Status " + subject.status() + ")");
	}

	@Component
	public static class Contains implements Eval {
		@Override
		public String type() {
			return "contains";
		}

		@Override
		public String label() {
			return "Antwort enth\u00e4lt Text";
		}

		@Override
		public String description() {
			return "Besteht, wenn der Text irgendwo in der Antwort vorkommt.";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(EvalParam.required("text", "Text", Type.STRING, "Muss in der Antwort vorkommen."), CASE);
		}

		@Override
		public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
			if (!subject.hasAnswer()) {
				return noAnswer(subject);
			}
			String text = config.string("text");
			return contains(config, subject)
					? EvalResult.pass("Antwort enth\u00e4lt \"" + text + "\"")
					: EvalResult.fail("Antwort enth\u00e4lt \"" + text + "\" nicht");
		}
	}

	@Component
	public static class NotContains implements Eval {
		@Override
		public String type() {
			return "not-contains";
		}

		@Override
		public String label() {
			return "Antwort enth\u00e4lt Text nicht";
		}

		@Override
		public String description() {
			return "Besteht, wenn der Text nirgends in der Antwort vorkommt.";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(EvalParam.required("text", "Text", Type.STRING, "Darf nicht in der Antwort vorkommen."), CASE);
		}

		@Override
		public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
			if (!subject.hasAnswer()) {
				return noAnswer(subject);
			}
			String text = config.string("text");
			return contains(config, subject)
					? EvalResult.fail("Antwort enth\u00e4lt \"" + text + "\"")
					: EvalResult.pass("Antwort enth\u00e4lt \"" + text + "\" nicht");
		}
	}

	@Component
	public static class Regex implements Eval {
		@Override
		public String type() {
			return "regex";
		}

		@Override
		public String label() {
			return "Antwort passt auf Muster";
		}

		@Override
		public String description() {
			return "Besteht, wenn der regul\u00e4re Ausdruck irgendwo in der Antwort trifft (Java-Syntax).";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(EvalParam.required("pattern", "Regul\u00e4rer Ausdruck", Type.STRING,
					"Java-Regex, z. B. \\b14\\b"), EvalParam.optional("ignore-case", CASE.label(), Type.BOOLEAN, false,
					CASE.description()));
		}

		@Override
		public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
			Pattern pattern = pattern(config);
			if (!subject.hasAnswer()) {
				return noAnswer(subject);
			}
			var matcher = pattern.matcher(subject.answer());
			return matcher.find()
					? EvalResult.pass("Muster trifft: \"" + matcher.group() + "\"")
					: EvalResult.fail("Muster /" + pattern.pattern() + "/ trifft nicht");
		}
	}

	@Component
	public static class NotRegex implements Eval {
		@Override
		public String type() {
			return "not-regex";
		}

		@Override
		public String label() {
			return "Antwort passt nicht auf Muster";
		}

		@Override
		public String description() {
			return "Besteht, wenn der regul\u00e4re Ausdruck nirgends in der Antwort trifft.";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(EvalParam.required("pattern", "Regul\u00e4rer Ausdruck", Type.STRING, "Java-Regex"),
					EvalParam.optional("ignore-case", CASE.label(), Type.BOOLEAN, false, CASE.description()));
		}

		@Override
		public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
			Pattern pattern = pattern(config);
			if (!subject.hasAnswer()) {
				return noAnswer(subject);
			}
			var matcher = pattern.matcher(subject.answer());
			return matcher.find()
					? EvalResult.fail("Muster trifft: \"" + matcher.group() + "\"")
					: EvalResult.pass("Muster /" + pattern.pattern() + "/ trifft nicht");
		}
	}
}
