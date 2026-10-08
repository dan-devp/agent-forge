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

	private static final EvalParam CASE = EvalParam.optional("ignore-case", "Ignore case",
			Type.BOOLEAN, true, "Compare without regard to upper and lower case.");

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
		return EvalResult.fail("No answer (status " + subject.status() + ")");
	}

	@Component
	public static class Contains implements Eval {
		@Override
		public String type() {
			return "contains";
		}

		@Override
		public String label() {
			return "Answer contains text";
		}

		@Override
		public String description() {
			return "Passes when the text appears anywhere in the answer.";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(EvalParam.required("text", "Text", Type.STRING, "Must appear in the answer."), CASE);
		}

		@Override
		public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
			if (!subject.hasAnswer()) {
				return noAnswer(subject);
			}
			String text = config.string("text");
			return contains(config, subject)
					? EvalResult.pass("Answer contains \"" + text + "\"")
					: EvalResult.fail("Answer does not contain \"" + text + "\"");
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
			return "Answer does not contain text";
		}

		@Override
		public String description() {
			return "Passes when the text appears nowhere in the answer.";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(EvalParam.required("text", "Text", Type.STRING, "Must not appear in the answer."), CASE);
		}

		@Override
		public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
			if (!subject.hasAnswer()) {
				return noAnswer(subject);
			}
			String text = config.string("text");
			return contains(config, subject)
					? EvalResult.fail("Answer contains \"" + text + "\"")
					: EvalResult.pass("Answer does not contain \"" + text + "\"");
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
			return "Answer matches pattern";
		}

		@Override
		public String description() {
			return "Passes when the regular expression matches anywhere in the answer (Java syntax).";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(EvalParam.required("pattern", "Regular expression", Type.STRING,
					"Java regex, e.g. \\b14\\b"), EvalParam.optional("ignore-case", CASE.label(), Type.BOOLEAN, false,
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
					? EvalResult.pass("Pattern matches: \"" + matcher.group() + "\"")
					: EvalResult.fail("Pattern /" + pattern.pattern() + "/ does not match");
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
			return "Answer does not match pattern";
		}

		@Override
		public String description() {
			return "Passes when the regular expression matches nowhere in the answer.";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(EvalParam.required("pattern", "Regular expression", Type.STRING, "Java regex"),
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
					? EvalResult.fail("Pattern matches: \"" + matcher.group() + "\"")
					: EvalResult.pass("Pattern /" + pattern.pattern() + "/ does not match");
		}
	}
}
