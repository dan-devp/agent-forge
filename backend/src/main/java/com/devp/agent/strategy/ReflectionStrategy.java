package com.devp.agent.strategy;

import java.util.List;
import java.util.stream.Collectors;

import com.devp.agent.profile.Profile.StrategyConfig;
import com.devp.agent.run.AgentRun;
import com.devp.agent.run.RunScore;
import com.devp.agent.run.RunStep;
import com.devp.agent.run.StepType;
import com.devp.agent.training.TrainingMemory;

import org.springframework.stereotype.Component;

/**
 * Reflexion: what went wrong last time goes into the next prompt.
 *
 * <p>The oldest and most widely shipped form of learning without weight updates. After a run
 * it writes one lesson into the training's memory; before the next run it puts the last few
 * lessons in front of the model.
 *
 * <p><strong>The lesson is drawn from the run's own trace, not from the grader's rationale.</strong>
 * A rationale names the criteria, and an agent that reads them learns the answer key rather
 * than the task — the pass rate rises without any capability behind it. The verdict is used
 * only as a trigger: did this run fail at all. Whoever wants the other behaviour switches
 * {@code include-rationale} on deliberately and must then measure on a held-out task.
 *
 * <p>Parameters: {@code max-lessons} (default 5), {@code only-on-failure} (default true),
 * {@code include-rationale} (default false).
 */
@Component
public class ReflectionStrategy implements Strategy {

	public static final String ID = "reflexion";

	/** Kind under which lessons are stored — the store is scoped by strategy anyway. */
	static final String LESSON = "lesson";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void beforeRun(RunContext context, StrategyConfig config) {
		List<TrainingMemory> lessons = context.memory().read(ID, LESSON);
		if (lessons.isEmpty()) {
			return;
		}
		int max = Math.max(1, config.intParam("max-lessons", 5));
		List<TrainingMemory> recent = lessons.size() <= max ? lessons : lessons.subList(lessons.size() - max, lessons.size());

		// German on purpose: this goes to the model.
		StringBuilder block = new StringBuilder("# Lektionen aus frueheren Versuchen\n");
		block.append("Das folgende stammt aus deinen eigenen vorigen Laeufen an dieser Aufgabe. ")
				.append("Nutze es, um denselben Fehler nicht zu wiederholen.\n");
		int i = 1;
		for (TrainingMemory lesson : recent) {
			block.append(i++).append(". ").append(lesson.content()).append('\n');
		}
		context.addToPrompt(block.toString());
	}

	@Override
	public void afterRun(RunContext context, StrategyConfig config, AgentRun run, List<RunScore> scores) {
		boolean graded = !scores.isEmpty();
		boolean passed = graded && scores.stream().allMatch(RunScore::passed);
		if (passed && Boolean.parseBoolean(config.param("only-on-failure", "true"))) {
			return;
		}
		context.memory().write(ID, LESSON, lesson(run, scores, config));
	}

	/** What is observable without the rubric: how the run ended, what it did, what it produced. */
	private static String lesson(AgentRun run, List<RunScore> scores, StrategyConfig config) {
		List<String> tools = run.trace().stream()
				.filter(step -> step.type() == StepType.TOOL)
				.map(RunStep::name)
				.toList();

		StringBuilder lesson = new StringBuilder("Versuch endete als ").append(run.status()).append(". ");
		lesson.append(tools.isEmpty()
				? "Es wurde kein Werkzeug benutzt. "
				: "Benutzte Werkzeuge: " + tools.stream().distinct().collect(Collectors.joining(", "))
						+ " (" + tools.size() + " Aufruf(e)). ");

		if (run.error() != null && !run.error().isBlank()) {
			lesson.append("Fehler: ").append(shorten(run.error(), 200)).append(' ');
		}
		if (run.answer() == null || run.answer().isBlank()) {
			lesson.append("Es kam keine Antwort zustande. ");
		}
		else {
			lesson.append("Abgegebene Antwort: \"").append(shorten(run.answer(), 300)).append("\" ");
		}
		lesson.append("Das wurde nicht anerkannt.");

		if (Boolean.parseBoolean(config.param("include-rationale", "false"))) {
			scores.stream()
					.filter(score -> !score.passed())
					.findFirst()
					.ifPresent(score -> lesson.append(" Begruendung der Bewertung: ")
							.append(shorten(score.rationale(), 300)));
		}
		return lesson.toString().strip();
	}

	private static String shorten(String text, int max) {
		String single = text.replace('\n', ' ').strip();
		return single.length() <= max ? single : single.substring(0, max) + " …";
	}
}
