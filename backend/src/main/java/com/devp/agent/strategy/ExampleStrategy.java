package com.devp.agent.strategy;

import java.util.List;

import com.devp.agent.profile.Profile.StrategyConfig;
import com.devp.agent.run.AgentRun;
import com.devp.agent.run.RunScore;
import com.devp.agent.training.TrainingMemory;

import org.springframework.stereotype.Component;

/**
 * Examples: answers that were accepted become the pattern for the next run.
 *
 * <p>The positive counterpart to Reflexion, and the interesting comparison to it: one shows
 * what not to do again, the other what worked. Which of the two carries is not obvious, and
 * that is exactly why both exist as separate profiles instead of one clever strategy.
 *
 * <p>Only answers of runs every grader passed are kept — a half-accepted answer as a model to
 * copy would teach the half that was wrong along with it.
 *
 * <p>Parameters: {@code max-examples} (default 2), {@code max-chars} (default 600).
 */
@Component
public class ExampleStrategy implements Strategy {

	public static final String ID = "beispiele";

	static final String EXAMPLE = "example";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void beforeRun(RunContext context, StrategyConfig config) {
		List<TrainingMemory> examples = context.memory().read(ID, EXAMPLE);
		if (examples.isEmpty()) {
			return;
		}
		int max = Math.max(1, config.intParam("max-examples", 2));
		List<TrainingMemory> recent = examples.size() <= max
				? examples
				: examples.subList(examples.size() - max, examples.size());

		// German on purpose: this goes to the model.
		StringBuilder block = new StringBuilder("# Beispiele aus bestandenen Versuchen\n");
		block.append("So sahen Antworten aus, die an dieser Aufgabe anerkannt wurden. ")
				.append("Nimm sie als Muster fuer Form und Genauigkeit, nicht als Inhalt zum Abschreiben.\n");
		int i = 1;
		for (TrainingMemory example : recent) {
			block.append("\nBeispiel ").append(i++).append(":\n").append(example.content()).append('\n');
		}
		context.addToPrompt(block.toString());
	}

	@Override
	public void afterRun(RunContext context, StrategyConfig config, AgentRun run, List<RunScore> scores) {
		boolean passed = !scores.isEmpty() && scores.stream().allMatch(RunScore::passed);
		if (!passed || run.answer() == null || run.answer().isBlank()) {
			return;
		}
		int maxChars = Math.max(100, config.intParam("max-chars", 600));
		String answer = run.answer().strip();
		context.memory().write(ID, EXAMPLE,
				answer.length() <= maxChars ? answer : answer.substring(0, maxChars) + " …");
	}
}
