package com.devp.agent.eval;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
			boolean ok = CriterionCheck.satisfied(criterion, answer, calledTools);
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

}
