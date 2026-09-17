package com.devp.agent.eval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.devp.agent.run.AgentRun;
import com.devp.agent.run.RunScore;
import com.devp.agent.run.RunStatus;
import com.devp.agent.run.RunStep;
import com.devp.agent.run.StepType;
import com.devp.agent.task.Task;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicGraderTests {

	private final DeterministicGrader grader = new DeterministicGrader();

	private static Task task(Criterion... criteria) {
		return new Task("t", "T", 1, null, 1, List.of(), null, null, null, null, null, List.of(criteria),
				"Do something.", Map.of(), "t.md");
	}

	private static AgentRun run(RunStatus status, String answer, String... tools) {
		List<RunStep> trace = new ArrayList<>();
		for (String tool : tools) {
			trace.add(new RunStep(trace.size() + 1, StepType.TOOL, tool, "{}", "x", null, 1L, null, Instant.now()));
		}
		return new AgentRun(UUID.randomUUID(), "Do something.", "t", 1, "forge", 1, "local", "m", status, answer,
				null, trace.size(), null, null, Instant.now(), Instant.now(), trace);
	}

	private static Criterion c(CriterionType type, String value) {
		return new Criterion(type, value, null);
	}

	@Test
	void noCriteriaNoVerdict() {
		assertThat(grader.grade(run(RunStatus.DONE, "whatever"), task())).isEmpty();
		assertThat(grader.grade(run(RunStatus.DONE, "whatever"), null)).isEmpty();
	}

	@Test
	void passesWhenAllCriteriaSatisfied() {
		Task task = task(c(CriterionType.CONTAINS, "rest 2"), c(CriterionType.TOOL_CALLED, "divide"));

		RunScore score = grader.grade(run(RunStatus.DONE, "100 / 7 = 14 Rest 2", "divide"), task).orElseThrow();

		assertThat(score.grader()).isEqualTo("deterministic");
		assertThat(score.passed()).isTrue();
		assertThat(score.score()).isEqualTo(1.0);
		assertThat(score.rationale()).contains("OK").doesNotContain("FAILED");
	}

	/** score is the share — so a comparison resolves finer than passed/failed. */
	@Test
	void partialSuccessYieldsShare() {
		Task task = task(c(CriterionType.CONTAINS, "14"), c(CriterionType.TOOL_CALLED, "divide"),
				c(CriterionType.TOOL_CALLED, "currentTime"));

		RunScore score = grader.grade(run(RunStatus.DONE, "Es ergibt 14.", "divide"), task).orElseThrow();

		assertThat(score.passed()).isFalse();
		assertThat(score.score()).isCloseTo(2.0 / 3, Offset.offset(0.0001));
		assertThat(score.rationale()).contains("FAILED  tool-called \"currentTime\"");
	}

	@Test
	void regexAndNegations() {
		Task task = task(
				c(CriterionType.REGEX, "\\b14\\b"),
				c(CriterionType.NOT_REGEX, "\\d+\\s*€"),
				c(CriterionType.NOT_CONTAINS, "erfunden"),
				c(CriterionType.TOOL_NOT_CALLED, "weather"));

		assertThat(grader.grade(run(RunStatus.DONE, "Ergebnis: 14", "divide"), task).orElseThrow().passed())
				.isTrue();
		// 140 instead of 14, a euro amount, tool weather called — only not-contains "erfunden" holds.
		assertThat(grader.grade(run(RunStatus.DONE, "Ergebnis: 140 €", "weather"), task).orElseThrow().score())
				.isEqualTo(0.25);
	}

	/** Star means: no tool at all — for tasks where any call is busywork. */
	@Test
	void starDemandsNoToolCallAtAll() {
		Task task = task(c(CriterionType.TOOL_NOT_CALLED, "*"));

		assertThat(grader.grade(run(RunStatus.DONE, "Weiss ich nicht."), task).orElseThrow().passed()).isTrue();
		assertThat(grader.grade(run(RunStatus.DONE, "Weiss ich nicht.", "divide"), task).orElseThrow().passed())
				.isFalse();
	}

	/**
	 * Without an answer even a negative criterion fails: a run that delivered nothing must not
	 * pass as "said nothing wrong".
	 */
	@Test
	void withoutAnswerTextCriteriaFail() {
		Task task = task(c(CriterionType.NOT_CONTAINS, "Euro"), c(CriterionType.TOOL_NOT_CALLED, "*"));

		RunScore score = grader.grade(run(RunStatus.BUDGET_EXCEEDED, null), task).orElseThrow();

		assertThat(score.passed()).isFalse();
		assertThat(score.score()).isEqualTo(0.5);
		assertThat(score.rationale()).contains("no answer").contains("BUDGET_EXCEEDED");
	}

	@Test
	void containsIgnoresCase() {
		Optional<RunScore> score = grader.grade(run(RunStatus.DONE, "REST 2"),
				task(c(CriterionType.CONTAINS, "rest 2")));
		assertThat(score.orElseThrow().passed()).isTrue();
	}
}
