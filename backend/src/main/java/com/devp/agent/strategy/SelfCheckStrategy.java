package com.devp.agent.strategy;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.devp.agent.eval.Criterion;
import com.devp.agent.eval.CriterionCheck;
import com.devp.agent.profile.Profile.StrategyConfig;
import com.devp.agent.task.Task;

import org.springframework.stereotype.Component;

/**
 * Self-check: the answer is held against the task's criteria before it is handed in.
 *
 * <p>The cheapest form of grounding there is. Pure self-criticism runs into the coherence
 * trap — the agent agrees with itself — so this strategy does not ask the model whether it is
 * happy; it checks the same rule the grader will apply and sends the answer back with what is
 * missing. No extra model call for the check itself, only for the revision.
 *
 * <p>Only criteria that can be judged from the answer are checked. Whether a tool was called
 * is in the trace, which does not exist yet while the answer is being written — claiming
 * otherwise would make the feedback lie.
 *
 * <p>{@code reveal} (default true) decides whether the feedback names the unmet criterion.
 * Named, this is the "rubric is known" pattern and works well; unnamed, it only says that
 * something is missing, which is the harder and more honest setting. The difference belongs
 * in an experiment, not in an opinion.
 *
 * <p>Parameters: {@code max-rounds} (default 1), {@code reveal} (default true).
 */
@Component
public class SelfCheckStrategy implements Strategy {

	public static final String ID = "selbstpruefung";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Optional<String> beforeFinish(RunContext context, StrategyConfig config, String answer) {
		Task task = context.task();
		if (task == null || task.success().isEmpty()) {
			// A free-form goal has no rubric — there is nothing to check against.
			return Optional.empty();
		}

		List<Criterion> unmet = task.success().stream()
				.filter(CriterionCheck::answerOnly)
				.filter(criterion -> !CriterionCheck.satisfied(criterion, answer, List.of()))
				.toList();

		if (unmet.isEmpty()) {
			return Optional.empty();
		}

		// German on purpose: this goes to the model.
		if (!Boolean.parseBoolean(config.param("reveal", "true"))) {
			return Optional.of("Deine Antwort erfuellt %d der Anforderungen dieser Aufgabe nicht. Pruefe sie noch einmal "
					.formatted(unmet.size()) + "gegen den Auftrag und liefere sie vollstaendig.");
		}
		return Optional.of("Deine Antwort erfuellt folgende Anforderung(en) noch nicht: "
				+ unmet.stream().map(Criterion::display).collect(Collectors.joining("; "))
				+ ". Ueberarbeite sie entsprechend.");
	}
}
