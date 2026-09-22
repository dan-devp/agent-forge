package com.devp.agent.strategy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.devp.agent.eval.Criterion;
import com.devp.agent.eval.CriterionType;
import com.devp.agent.profile.Profile.StrategyConfig;
import com.devp.agent.run.AgentRun;
import com.devp.agent.run.RunScore;
import com.devp.agent.run.RunStatus;
import com.devp.agent.run.RunStep;
import com.devp.agent.run.StepType;
import com.devp.agent.task.Task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the three learning strategies actually put in front of the model, and what they refuse
 * to put there. No Spring, no database: a strategy is a function of context and run.
 */
class StrategyBehaviourTests {

	private static final Criterion CONTAINS_REST = new Criterion(CriterionType.CONTAINS, "Rest", null);

	private static final Criterion TOOL_USED = new Criterion(CriterionType.TOOL_CALLED, "divide", null);

	private static Task task(Criterion... criteria) {
		return new Task("t", "Aufgabe", 1, null, 1, List.of(), null, null, null, null, null, List.of(criteria),
				"Rechne.", Map.of(), "t.md");
	}

	private static RunContext context(MemoryStore memory, Task task) {
		return new RunContext(UUID.randomUUID(), "Rechne.", task, null, null, memory);
	}

	private static StrategyConfig config(Map<String, Object> params) {
		return new StrategyConfig("x", params);
	}

	private static AgentRun run(RunStatus status, String answer, String error, List<RunStep> trace) {
		return new AgentRun(UUID.randomUUID(), "Rechne.", "t", 1, "forge", 1, "local", "m", status, answer, error, 1,
				10, 20L, Instant.now(), Instant.now(), trace);
	}

	private static RunStep toolStep(String name) {
		return new RunStep(1, StepType.TOOL, name, "{}", "ok", null, 5L, null, Instant.now());
	}

	private static RunScore score(boolean passed) {
		return new RunScore("deterministic", passed ? 1.0 : 0.0, passed,
				"FAILED  contains \"Rest\"", Instant.now());
	}

	// --- Reflexion ----------------------------------------------------------

	@Test
	void reflectionWritesALessonFromTheTraceAndReadsItBack() {
		ReflectionStrategy strategy = new ReflectionStrategy();
		FakeMemoryStore memory = new FakeMemoryStore();
		StrategyConfig config = config(Map.of());

		strategy.afterRun(context(memory, task(CONTAINS_REST)), config,
				run(RunStatus.DONE, "Das Ergebnis ist 14.", null, List.of(toolStep("divide"))), List.of(score(false)));

		assertThat(memory.contents()).singleElement().asString()
				.contains("DONE")
				.contains("divide")
				.contains("Das Ergebnis ist 14.");

		RunContext next = context(memory, task(CONTAINS_REST));
		strategy.beforeRun(next, config);

		assertThat(next.promptAdditions()).singleElement().asString()
				.contains("Lektionen aus frueheren Versuchen")
				.contains("Das Ergebnis ist 14.");
	}

	/** The rubric stays out of the prompt: otherwise the agent learns the answer key, not the task. */
	@Test
	void reflectionKeepsTheGraderRationaleOutUnlessAsked() {
		ReflectionStrategy strategy = new ReflectionStrategy();
		FakeMemoryStore quiet = new FakeMemoryStore();
		FakeMemoryStore loud = new FakeMemoryStore();

		strategy.afterRun(context(quiet, task(CONTAINS_REST)), config(Map.of()),
				run(RunStatus.DONE, "14", null, List.of()), List.of(score(false)));
		strategy.afterRun(context(loud, task(CONTAINS_REST)), config(Map.of("include-rationale", true)),
				run(RunStatus.DONE, "14", null, List.of()), List.of(score(false)));

		assertThat(quiet.contents().getFirst()).doesNotContain("contains \"Rest\"");
		assertThat(loud.contents().getFirst()).contains("contains \"Rest\"");
	}

	@Test
	void reflectionStaysSilentOnASuccessfulRun() {
		ReflectionStrategy strategy = new ReflectionStrategy();
		FakeMemoryStore memory = new FakeMemoryStore();

		strategy.afterRun(context(memory, task(CONTAINS_REST)), config(Map.of()),
				run(RunStatus.DONE, "Rest 2", null, List.of()), List.of(score(true)));

		assertThat(memory.contents()).isEmpty();
	}

	@Test
	void reflectionOnlyCarriesTheMostRecentLessons() {
		ReflectionStrategy strategy = new ReflectionStrategy();
		FakeMemoryStore memory = new FakeMemoryStore();
		StrategyConfig config = config(Map.of("max-lessons", 2));

		for (int i = 1; i <= 4; i++) {
			strategy.afterRun(context(memory, task(CONTAINS_REST)), config,
					run(RunStatus.DONE, "Versuch " + i, null, List.of()), List.of(score(false)));
		}
		RunContext next = context(memory, task(CONTAINS_REST));
		strategy.beforeRun(next, config);

		String prompt = next.promptAdditions().getFirst();
		assertThat(prompt).contains("Versuch 3", "Versuch 4").doesNotContain("Versuch 1");
	}

	// --- Selbstpruefung -----------------------------------------------------

	@Test
	void selfCheckRejectsAnAnswerThatMissesACriterion() {
		SelfCheckStrategy strategy = new SelfCheckStrategy();

		assertThat(strategy.beforeFinish(context(new FakeMemoryStore(), task(CONTAINS_REST)), config(Map.of()), "14"))
				.get().asString().contains("Rest");
		assertThat(strategy.beforeFinish(context(new FakeMemoryStore(), task(CONTAINS_REST)), config(Map.of()),
				"Rest 2, Ergebnis 14")).isEmpty();
	}

	/** Without reveal the feedback says that something is missing, not what. */
	@Test
	void selfCheckCanWithholdTheCriterion() {
		SelfCheckStrategy strategy = new SelfCheckStrategy();

		assertThat(strategy.beforeFinish(context(new FakeMemoryStore(), task(CONTAINS_REST)),
				config(Map.of("reveal", false)), "14"))
				.get().asString().doesNotContain("Rest").contains("nicht");
	}

	/** Whether a tool was called is in the trace, which does not exist yet — so it is not claimed. */
	@Test
	void selfCheckIgnoresCriteriaItCannotSeeYet() {
		SelfCheckStrategy strategy = new SelfCheckStrategy();

		assertThat(strategy.beforeFinish(context(new FakeMemoryStore(), task(TOOL_USED)), config(Map.of()), "irgendwas"))
				.isEmpty();
	}

	@Test
	void selfCheckHasNothingToDoWithoutARubric() {
		SelfCheckStrategy strategy = new SelfCheckStrategy();

		assertThat(strategy.beforeFinish(context(new FakeMemoryStore(), null), config(Map.of()), "irgendwas")).isEmpty();
		assertThat(strategy.beforeFinish(context(new FakeMemoryStore(), task()), config(Map.of()), "irgendwas"))
				.isEmpty();
	}

	// --- Beispiele ----------------------------------------------------------

	@Test
	void examplesKeepOnlyAcceptedAnswers() {
		ExampleStrategy strategy = new ExampleStrategy();
		FakeMemoryStore memory = new FakeMemoryStore();

		strategy.afterRun(context(memory, task(CONTAINS_REST)), config(Map.of()),
				run(RunStatus.DONE, "falsch", null, List.of()), List.of(score(false)));
		strategy.afterRun(context(memory, task(CONTAINS_REST)), config(Map.of()),
				run(RunStatus.DONE, "Rest 2, Ergebnis 14", null, List.of()), List.of(score(true)));

		assertThat(memory.contents()).containsExactly("Rest 2, Ergebnis 14");

		RunContext next = context(memory, task(CONTAINS_REST));
		strategy.beforeRun(next, config(Map.of()));

		assertThat(next.promptAdditions()).singleElement().asString()
				.contains("Beispiele aus bestandenen Versuchen")
				.contains("Rest 2, Ergebnis 14");
	}

	@Test
	void examplesStayQuietUntilSomethingHasPassed() {
		ExampleStrategy strategy = new ExampleStrategy();
		RunContext context = context(new FakeMemoryStore(), task(CONTAINS_REST));

		strategy.beforeRun(context, config(Map.of()));

		assertThat(context.promptAdditions()).isEmpty();
	}

	@Test
	void examplesAreTruncatedSoThePromptStaysBounded() {
		ExampleStrategy strategy = new ExampleStrategy();
		FakeMemoryStore memory = new FakeMemoryStore();

		strategy.afterRun(context(memory, task(CONTAINS_REST)), config(Map.of("max-chars", 100)),
				run(RunStatus.DONE, "x".repeat(500), null, List.of()), List.of(score(true)));

		assertThat(memory.contents().getFirst()).hasSizeLessThan(150).endsWith("…");
	}
}
