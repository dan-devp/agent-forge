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
			return "Tool was called";
		}

		@Override
		public String description() {
			return "Passes when the tool was called at least once in the run.";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(EvalParam.required("tool", "Tool", Type.TOOL, "Name of the tool, e.g. currentTime"));
		}

		@Override
		public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
			String tool = config.string("tool");
			long count = subject.calledTools().stream().filter(tool::equals).count();
			return count > 0
					? EvalResult.pass(tool + " was called " + count + "x")
					: EvalResult.fail(tool + " was not called (called: " + describe(subject) + ")");
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
			return "Tool was not called";
		}

		@Override
		public String description() {
			return "Passes when the tool was never called. '*' = no tool at all.";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(EvalParam.required("tool", "Tool", Type.TOOL, "Name of the tool, or * for all"));
		}

		@Override
		public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
			String tool = config.string("tool");
			if ("*".equals(tool)) {
				return subject.calledTools().isEmpty()
						? EvalResult.pass("No tool called")
						: EvalResult.fail("Tools called: " + describe(subject));
			}
			return subject.calledTools().contains(tool)
					? EvalResult.fail(tool + " was called")
					: EvalResult.pass(tool + " was not called");
		}
	}

	private static String describe(EvalSubject subject) {
		return subject.calledTools().isEmpty() ? "none" : String.join(", ", subject.calledTools());
	}
}
