package com.devp.forge.training;

import java.util.List;

import com.devp.forge.training.Bench.FailedRun;
import com.devp.forge.training.Bench.TaskScore;

/**
 * Everything the coach gets to see, and the one place that turns it into its message.
 *
 * <p>The coach reads German like every model here. It sees the prompt it should improve, the
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
		out.append("# Ziel des Trainings\n")
				.append(goal == null || goal.isBlank() ? "Die Tasks zuverlaessiger bestehen." : goal.strip())
				.append("\n\n");

		out.append("# Aktueller Systemprompt (v").append(agentVersion).append(")\n")
				.append("<<<\n").append(systemPrompt.strip()).append("\n>>>\n\n");

		out.append("# Tools des Agents\n");
		if (tools.isEmpty()) {
			out.append("Keine. Der Agent hat keine Tools, und du kannst ihm keine geben.\n\n");
		}
		else {
			tools.forEach(t -> out.append("- ").append(t).append('\n'));
			out.append("Die Tools kannst du nicht aendern, nur den Prompt.\n\n");
		}

		out.append("# Ergebnisse je Task (v").append(agentVersion).append(")\n");
		trainScores.forEach(s -> out.append("- ").append(s.taskId()).append(": ").append(s.passed()).append('/')
				.append(s.total()).append(" bestanden\n"));
		out.append('\n');

		if (!failures.isEmpty()) {
			out.append("# Gescheiterte Runs\n");
			int n = 0;
			for (FailedRun f : failures) {
				n++;
				out.append("## ").append(n).append(". Task ").append(f.taskId()).append(" (").append(status(f.status()))
						.append(")\n");
				out.append("Eingabe: ").append(oneLine(f.input())).append('\n');
				if (f.toolCalls().isEmpty()) {
					out.append("Tool-Aufrufe: keine\n");
				}
				else {
					out.append("Tool-Aufrufe:\n");
					f.toolCalls().forEach(c -> out.append("- ").append(oneLine(c)).append('\n'));
				}
				if (f.answer() != null) {
					out.append("Antwort: ").append(oneLine(f.answer())).append('\n');
				}
				if (f.error() != null) {
					out.append("Fehler: ").append(oneLine(f.error())).append('\n');
				}
				if (!f.failedVerdicts().isEmpty()) {
					out.append("Nicht erfuellt:\n");
					f.failedVerdicts().forEach(v -> out.append("- ").append(oneLine(v)).append('\n'));
				}
				out.append('\n');
			}
		}

		if (!rejected.isEmpty()) {
			out.append("# Bereits versucht und verworfen\n");
			rejected.forEach(a -> out.append("- ")
					.append(a.agentVersion() == null ? "ohne Version" : "v" + a.agentVersion()).append(": ")
					.append(oneLine(a.rationale() == null ? "(keine Begruendung)" : a.rationale())).append(" -> ")
					.append(oneLine(a.reason())).append('\n'));
			out.append("Schlage etwas anderes vor.\n\n");
		}

		out.append("""
				# Antwortformat
				Antworte in genau diesem Format:
				BEGRUENDUNG:
				- je Aenderung eine Zeile: was du aenderst und welcher Fehler oben sie ausloest
				PROMPT:
				<<<
				der vollstaendige neue Systemprompt
				>>>""");
		return out.toString();
	}

	private static String status(String status) {
		return switch (status) {
			case "DONE" -> "nicht bestanden";
			case "BUDGET_EXCEEDED" -> "Budget ueberschritten";
			case "FAILED" -> "Fehler";
			default -> status;
		};
	}

	private static String oneLine(String text) {
		return text == null ? "" : text.replaceAll("\\s+", " ").strip();
	}
}
