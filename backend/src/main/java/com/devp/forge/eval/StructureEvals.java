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
		return EvalResult.fail("No answer (status " + subject.status() + ")");
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
			return "Answer is exactly";
		}

		@Override
		public String description() {
			return "Passes when the whole answer equals the text. Whitespace at the edges does not count.";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(
					EvalParam.required("text", "Expected answer", Type.STRING, "The one right answer."),
					EvalParam.optional("ignore-case", "Ignore case", Type.BOOLEAN, true,
							"Compare without regard to upper and lower case."),
					EvalParam.optional("collapse-whitespace", "Collapse whitespace", Type.BOOLEAN, true,
							"Several spaces and line breaks count as one."));
		}

		@Override
		public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
			if (!subject.hasAnswer()) {
				return noAnswer(subject);
			}
			String expected = normalize(config.string("text"), config);
			String actual = normalize(subject.answer(), config);
			return expected.equals(actual)
					? EvalResult.pass("Answer equals \"" + config.string("text") + "\"")
					: EvalResult.fail("Answer is \"" + shorten(subject.answer().strip()) + "\", expected \""
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
			return "Answer length";
		}

		@Override
		public String description() {
			return "Passes when the answer is between min and max (characters or words). Give at least one of the two.";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(
					EvalParam.optional("min", "At least", Type.NUMBER, null, "Lower bound, empty = none."),
					EvalParam.optional("max", "At most", Type.NUMBER, null, "Upper bound, empty = none."),
					EvalParam.optional("unit", "Unit", Type.STRING, "words", "words or chars."));
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
			String label = unit.equals("chars") ? "characters" : "words";
			if (min >= 0 && count < min) {
				return EvalResult.fail("%d %s, at least %d required".formatted(count, label, min));
			}
			if (max >= 0 && count > max) {
				return EvalResult.fail("%d %s, at most %d allowed".formatted(count, label, max));
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
			return "At most N tool calls";
		}

		@Override
		public String description() {
			return "Passes when the run made no more than N tool calls. Independent of the task's budget.";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(EvalParam.required("max", "At most", Type.NUMBER, "Calls allowed in total, 0 = none."));
		}

		@Override
		public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
			int max = intParam(config, "max", -1);
			if (max < 0) {
				throw new EntityInvalidException("'max' must be 0 or more");
			}
			int calls = subject.calledTools().size();
			return calls <= max
					? EvalResult.pass("%d tool call(s), %d allowed".formatted(calls, max))
					: EvalResult.fail("%d tool calls, %d allowed (%s)".formatted(calls, max,
							String.join(", ", subject.calledTools())));
		}
	}

	private static String shorten(String text) {
		return text.length() <= 80 ? text : text.substring(0, 77) + "...";
	}
}
