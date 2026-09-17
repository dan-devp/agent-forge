package com.devp.agent.eval;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import com.devp.agent.run.AgentRun;
import com.devp.agent.run.RunScore;
import com.devp.agent.run.RunStep;
import com.devp.agent.run.StepType;
import com.devp.agent.task.Task;

import org.springframework.stereotype.Component;

/**
 * Checks the {@code success:} criteria against answer and trace. No model involved — free,
 * incorruptible, and therefore the grader every task gets first.
 *
 * <p>{@code score} is the share of satisfied criteria, {@code passed} requires all of them.
 * Without an answer (budget, failure) text criteria fail — including the negative ones: a run
 * that delivered nothing did nothing right.
 */
@Component
public class DeterministicGrader implements Grader {

	public static final String ID = "deterministic";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Optional<RunScore> grade(AgentRun run, Task task) {
		if (task == null || task.success().isEmpty()) {
			return Optional.empty();
		}

		List<String> calledTools = run.trace().stream()
				.filter(step -> step.type() == StepType.TOOL)
				.map(RunStep::name)
				.toList();
		String answer = run.answer();

		StringBuilder rationale = new StringBuilder();
		int satisfied = 0;
		for (Criterion criterion : task.success()) {
			boolean ok = check(criterion, answer, calledTools);
			if (ok) {
				satisfied++;
			}
			rationale.append(ok ? "OK    " : "FAILED").append("  ").append(criterion.display()).append('\n');
		}
		if (answer == null) {
			rationale.append("(no answer, status ").append(run.status()).append(')');
		}

		int total = task.success().size();
		return Optional.of(new RunScore(ID, (double) satisfied / total, satisfied == total,
				rationale.toString().strip(), Instant.now()));
	}

	private static boolean check(Criterion criterion, String answer, List<String> tools) {
		return switch (criterion.type()) {
			case CONTAINS -> answer != null && lower(answer).contains(lower(criterion.value()));
			case NOT_CONTAINS -> answer != null && !lower(answer).contains(lower(criterion.value()));
			case REGEX -> answer != null && Pattern.compile(criterion.value()).matcher(answer).find();
			case NOT_REGEX -> answer != null && !Pattern.compile(criterion.value()).matcher(answer).find();
			case TOOL_CALLED -> tools.contains(criterion.value());
			case TOOL_NOT_CALLED -> "*".equals(criterion.value()) ? tools.isEmpty() : !tools.contains(criterion.value());
		};
	}

	private static String lower(String text) {
		return text.toLowerCase(Locale.ROOT);
	}
}
