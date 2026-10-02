package com.devp.forge.eval;

import java.util.List;

import com.devp.forge.eval.EvalParam.Type;

import org.springframework.stereotype.Component;

/**
 * Evals on the trace rather than the answer: did the agent use its tools? A correct number
 * from the head is a guess that happened to work; the same number from the tool is the
 * behaviour the task wanted.
 */
public final class ToolEvals {

	private ToolEvals() {
	}

	@Component
	public static class ToolCalled implements Eval {
		@Override
		public String type() {
			return "tool-called";
		}

		@Override
		public String label() {
			return "Tool wurde aufgerufen";
		}

		@Override
		public String description() {
			return "Besteht, wenn das Tool im Run mindestens einmal aufgerufen wurde.";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(EvalParam.required("tool", "Tool", Type.TOOL, "Name des Tools, z. B. currentTime"));
		}

		@Override
		public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
			String tool = config.string("tool");
			long count = subject.calledTools().stream().filter(tool::equals).count();
			return count > 0
					? EvalResult.pass(tool + " wurde " + count + "x aufgerufen")
					: EvalResult.fail(tool + " wurde nicht aufgerufen (aufgerufen: " + describe(subject) + ")");
		}
	}

	@Component
	public static class ToolNotCalled implements Eval {
		@Override
		public String type() {
			return "tool-not-called";
		}

		@Override
		public String label() {
			return "Tool wurde nicht aufgerufen";
		}

		@Override
		public String description() {
			return "Besteht, wenn das Tool nie aufgerufen wurde. '*' = gar kein Tool.";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(EvalParam.required("tool", "Tool", Type.TOOL, "Name des Tools oder * f\u00fcr alle"));
		}

		@Override
		public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
			String tool = config.string("tool");
			if ("*".equals(tool)) {
				return subject.calledTools().isEmpty()
						? EvalResult.pass("Kein Tool aufgerufen")
						: EvalResult.fail("Tools aufgerufen: " + describe(subject));
			}
			return subject.calledTools().contains(tool)
					? EvalResult.fail(tool + " wurde aufgerufen")
					: EvalResult.pass(tool + " wurde nicht aufgerufen");
		}
	}

	private static String describe(EvalSubject subject) {
		return subject.calledTools().isEmpty() ? "keins" : String.join(", ", subject.calledTools());
	}
}
