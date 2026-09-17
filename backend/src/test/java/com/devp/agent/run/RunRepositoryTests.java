package com.devp.agent.run;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.devp.agent.support.PostgresTestcontainer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestcontainer.class)
class RunRepositoryTests {

	@Autowired
	private RunRepository repository;

	@BeforeEach
	void clear() {
		repository.deleteAll();
	}

	private AgentRun started(UUID id) {
		return new AgentRun(id, "Wie spaet ist es?", "time-and-division", 1, "forge", 1, "local", "mock-local-7b",
				RunStatus.RUNNING, null, null, 0, null, null, Instant.now(), null, List.of());
	}

	@Test
	void writesAndReadsARun() {
		UUID id = UUID.randomUUID();
		repository.start(started(id));

		AgentRun read = repository.find(id).orElseThrow();
		assertThat(read.goal()).isEqualTo("Wie spaet ist es?");
		assertThat(read.soulId()).isEqualTo("forge");
		assertThat(read.soulVersion()).isEqualTo(1);
		assertThat(read.status()).isEqualTo(RunStatus.RUNNING);
		assertThat(read.trace()).isEmpty();
		assertThat(read.scores()).isEmpty();
	}

	@Test
	void appendsStepsInOrder() {
		UUID id = UUID.randomUUID();
		repository.start(started(id));

		repository.addStep(id, new RunStep(repository.nextStepNumber(id), StepType.MODEL, "mock-local-7b",
				null, "requests tools: currentTime({})", 40, null, null, Instant.now()));
		repository.addStep(id, new RunStep(repository.nextStepNumber(id), StepType.TOOL, "currentTime",
				"{}", "2026-09-17 10:00:00", null, 12L, null, Instant.now()));

		List<RunStep> trace = repository.trace(id);
		assertThat(trace).extracting(RunStep::seq).containsExactly(1, 2);
		assertThat(trace).extracting(RunStep::type).containsExactly(StepType.MODEL, StepType.TOOL);
		assertThat(trace.get(1).output()).isEqualTo("2026-09-17 10:00:00");
		assertThat(trace.get(1).durationMs()).isEqualTo(12L);
	}

	/** nextStepNumber counts from the trace, not from a counter in memory. */
	@Test
	void assignsStepNumbersSequentially() {
		UUID id = UUID.randomUUID();
		repository.start(started(id));

		assertThat(repository.nextStepNumber(id)).isEqualTo(1);
		repository.addStep(id, new RunStep(1, StepType.MODEL, "m", null, "x", null, null, null, Instant.now()));
		assertThat(repository.nextStepNumber(id)).isEqualTo(2);
	}

	@Test
	void writesTheFinalState() {
		UUID id = UUID.randomUUID();
		AgentRun start = started(id);
		repository.start(start);

		Instant end = Instant.now();
		repository.finish(new AgentRun(id, start.goal(), start.taskId(), start.taskVersion(), start.soulId(),
				start.soulVersion(), start.providerId(), "mock-local-7b", RunStatus.DONE, "Es ist 10 Uhr.", null,
				2, 120, 850L, start.startedAt(), end, List.of()));

		AgentRun finished = repository.find(id).orElseThrow();
		assertThat(finished.status()).isEqualTo(RunStatus.DONE);
		assertThat(finished.answer()).isEqualTo("Es ist 10 Uhr.");
		assertThat(finished.totalTokens()).isEqualTo(120);
		assertThat(finished.durationMs()).isEqualTo(850L);
		assertThat(finished.finishedAt()).isNotNull();
	}

	@Test
	void findIncludesTheTrace() {
		UUID id = UUID.randomUUID();
		repository.start(started(id));
		repository.addStep(id, new RunStep(1, StepType.TOOL, "divide", "{\"dividend\":100}", "14 Rest 2",
				null, 5L, null, Instant.now()));

		assertThat(repository.find(id).orElseThrow().trace()).hasSize(1);
	}

	/** The history deliberately loads no trace — it would otherwise pull half the database. */
	@Test
	void listReturnsNewestFirstWithoutTrace() {
		UUID old = UUID.randomUUID();
		UUID recent = UUID.randomUUID();
		repository.start(new AgentRun(old, "old", null, null, "forge", 1, "local", null, RunStatus.RUNNING, null,
				null, 0, null, null, Instant.now().minusSeconds(60), null, List.of()));
		repository.start(new AgentRun(recent, "recent", null, null, "forge", 1, "local", null, RunStatus.RUNNING,
				null, null, 0, null, null, Instant.now(), null, List.of()));
		repository.addStep(recent, new RunStep(1, StepType.MODEL, "m", null, "x", null, null, null, Instant.now()));

		List<AgentRun> list = repository.list(10);
		assertThat(list).extracting(AgentRun::goal).containsExactly("recent", "old");
		assertThat(list.getFirst().trace()).isEmpty();
	}

	/**
	 * Task and version belong on the run — without the version a later comparison is
	 * worthless because a changed task produces different results unnoticed.
	 */
	@Test
	void remembersTaskAndVersion() {
		UUID id = UUID.randomUUID();
		repository.start(started(id));

		AgentRun read = repository.find(id).orElseThrow();
		assertThat(read.taskId()).isEqualTo("time-and-division");
		assertThat(read.taskVersion()).isEqualTo(1);
	}

	/** A free-form goal has no task — that must stay a clean null. */
	@Test
	void freeFormGoalHasNoTask() {
		UUID id = UUID.randomUUID();
		repository.start(new AgentRun(id, "typed freely", null, null, "forge", 1, "local", null,
				RunStatus.RUNNING, null, null, 0, null, null, Instant.now(), null, List.of()));

		AgentRun read = repository.find(id).orElseThrow();
		assertThat(read.taskId()).isNull();
		assertThat(read.taskVersion()).isNull();
	}

	/** Several graders side by side, none overwrites another — otherwise their comparison is lost. */
	@Test
	void appendsScoresAndReturnsThem() {
		UUID id = UUID.randomUUID();
		repository.start(started(id));

		repository.addScore(id, new RunScore("deterministic", 0.5, false, "FAILED  regex \"14\"", Instant.now()));
		repository.addScore(id, new RunScore("human", 1.0, true, "fine", Instant.now()));

		List<RunScore> scores = repository.find(id).orElseThrow().scores();
		assertThat(scores).extracting(RunScore::grader).containsExactly("deterministic", "human");
		assertThat(scores.getFirst().score()).isEqualTo(0.5);
		assertThat(scores.getFirst().passed()).isFalse();
		assertThat(scores.getFirst().rationale()).contains("FAILED");
		assertThat(repository.list(10).getFirst().scores()).isEmpty();
	}

	@Test
	void unknownRunIsEmpty() {
		assertThat(repository.find(UUID.randomUUID())).isEmpty();
	}
}
