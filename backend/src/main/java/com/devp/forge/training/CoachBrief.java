package com.devp.forge.training;

import java.util.List;

import com.devp.forge.training.Bench.FailedRun;
import com.devp.forge.training.Bench.TaskScore;

/**
 * Everything the coach gets to see, and the one place that turns it into its message.
 *
 * <p>The coach sees the prompt it should improve, the
 * tools the agent has, how each train task went and the failed runs in detail, plus what was
 * already tried and rejected — so it does not propose the same thing twice. It never sees the
 * holdout tasks, and never the evals themselves: only their verdicts, the way a student sees
 * the corrections but not the answer key.
 *
 * <p>The answer format is set here, not in the coach's system prompt: parsing must not depend
 * on a file someone can edit.
 */
public record CoachBrief(
		String goal,
		int agentVersion,
		String systemPrompt,
		List<String> tools,
		List<TaskScore> trainScores,
		List<FailedRun> failures,
		List<Attempt> rejected) {

	/** A snapshot: the engine keeps adding to its lists after the brief went out. */
	public CoachBrief {
		tools = List.copyOf(tools);
		trainScores = List.copyOf(trainScores);
		failures = List.copyOf(failures);
		rejected = List.copyOf(rejected);
	}

	/** A proposal that did not make it, and why. */
	public record Attempt(Integer agentVersion, String rationale, String reason) {
	}

	public String render() {
		StringBuilder out = new StringBuilder();
		out.append("# Goal of the training\n")
				.append(goal == null || goal.isBlank() ? "Pass the tasks more reliably." : goal.strip())
				.append("\n\n");

		out.append("# Current system prompt (v").append(agentVersion).append(")\n")
				.append("<<<\n").append(systemPrompt.strip()).append("\n>>>\n\n");

		out.append("# The agent's tools\n");
		if (tools.isEmpty()) {
			out.append("None. The agent has no tools, and you cannot give it any.\n\n");
		}
		else {
			tools.forEach(t -> out.append("- ").append(t).append('\n'));
			out.append("You cannot change the tools, only the prompt.\n\n");
		}

		out.append("# Results per task (v").append(agentVersion).append(")\n");
		trainScores.forEach(s -> out.append("- ").append(s.taskId()).append(": ").append(s.passed()).append('/')
				.append(s.total()).append(" passed\n"));
		out.append('\n');

		if (!failures.isEmpty()) {
			out.append("# Failed runs\n");
			int n = 0;
			for (FailedRun f : failures) {
				n++;
				out.append("## ").append(n).append(". Task ").append(f.taskId()).append(" (").append(status(f.status()))
						.append(")\n");
				out.append("Input: ").append(oneLine(f.input())).append('\n');
				if (f.toolCalls().isEmpty()) {
					out.append("Tool calls: none\n");
				}
				else {
					out.append("Tool calls:\n");
					f.toolCalls().forEach(c -> out.append("- ").append(oneLine(c)).append('\n'));
				}
				if (f.answer() != null) {
					out.append("Answer: ").append(oneLine(f.answer())).append('\n');
				}
				if (f.error() != null) {
					out.append("Error: ").append(oneLine(f.error())).append('\n');
				}
				if (!f.failedVerdicts().isEmpty()) {
					out.append("Not met:\n");
					f.failedVerdicts().forEach(v -> out.append("- ").append(oneLine(v)).append('\n'));
				}
				out.append('\n');
			}
		}

		if (!rejected.isEmpty()) {
			out.append("# Already tried and rejected\n");
			rejected.forEach(a -> out.append("- ")
					.append(a.agentVersion() == null ? "no version" : "v" + a.agentVersion()).append(": ")
					.append(oneLine(a.rationale() == null ? "(no rationale)" : a.rationale())).append(" -> ")
					.append(oneLine(a.reason())).append('\n'));
			out.append("Propose something different.\n\n");
		}

		out.append("""
				# Answer format
				Answer in exactly this format:
				RATIONALE:
				- one line per change: what you change and which failure above calls for it
				PROMPT:
				<<<
				the complete new system prompt
				>>>""");
		return out.toString();
	}

	private static String status(String status) {
		return switch (status) {
			case "DONE" -> "failed";
			case "BUDGET_EXCEEDED" -> "over budget";
			case "FAILED" -> "error";
			default -> status;
		};
	}

	private static String oneLine(String text) {
		return text == null ? "" : text.replaceAll("\\s+", " ").strip();
	}
}
