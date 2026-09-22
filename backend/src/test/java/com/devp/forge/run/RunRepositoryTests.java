package com.devp.forge.run;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.devp.forge.support.PostgresTestcontainer;

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

	static Run started(UUID id) {
		return queued(id, null, "time-and-division", "forge", 2).withStatus(RunStatus.RUNNING, Instant.now());
	}

	static Run queued(UUID id, UUID batchId, String taskId, String agentId, int agentVersion) {
		return new Run(id, batchId, taskId, 1, agentId, agentVersion, "local", 1, "mock-local-7b", 0.7, "Du bist Forge.",
				"Wie spaet ist es?", List.of("currentTime", "divide"), RunStatus.QUEUED, null, null, 0, null, null,
				null, Instant.now(), null, null, List.of(), List.of());
	}

	@Test
	void writesAndReadsARun() {
		UUID id = UUID.randomUUID();
		repository.create(started(id));

		Run read = repository.find(id).orElseThrow();
		assertThat(read.agentId()).isEqualTo("forge");
		assertThat(read.agentVersion()).isEqualTo(2);
		assertThat(read.modelId()).isEqualTo("local");
		assertThat(read.temperature()).isEqualTo(0.7);
		assertThat(read.systemPrompt()).isEqualTo("Du bist Forge.");
		assertThat(read.tools()).containsExactly("currentTime", "divide");
		assertThat(read.status()).isEqualTo(RunStatus.RUNNING);
		assertThat(read.startedAt()).isNotNull();
		assertThat(read.passed()).isNull();
		assertThat(read.trace()).isEmpty();
		assertThat(read.evals()).isEmpty();
	}

	@Test
	void appendsStepsAndEvalsInOrder() {
		UUID id = UUID.randomUUID();
		repository.create(started(id));

		repository.addStep(id, new RunStep(repository.nextStepNumber(id), StepType.MODEL, "mock", null,
				"requests tools: currentTime({})", 40, null, null, Instant.now()));
		repository.addStep(id, new RunStep(repository.nextStepNumber(id), StepType.TOOL, "currentTime", "{}",
				"2026-09-17 10:00:00", null, 12L, null, Instant.now()));
		repository.addEval(id, new RunEval(1, "tool-called", true, "ok", null, null, null, Instant.now()));
		repository.addEval(id, new RunEval(2, "llm-judge", false, "zu knapp", "judge-1", 120, 800L, Instant.now()));
		repository.setPassed(id, false);

		Run read = repository.find(id).orElseThrow();
		assertThat(read.trace()).extracting(RunStep::seq).containsExactly(1, 2);
		assertThat(read.trace()).extracting(RunStep::type).containsExactly(StepType.MODEL, StepType.TOOL);
		assertThat(repository.countToolSteps(id)).isEqualTo(1);
		assertThat(read.evals()).extracting(RunEval::index).containsExactly(1, 2);
		assertThat(read.evals().get(1).tokens()).isEqualTo(120);
		assertThat(read.passed()).isFalse();
	}

	@Test
	void listIsSummaryOnlyAndFilters() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID batch = UUID.randomUUID();
		repository.create(started(a));
		repository.create(queued(b, batch, "no-tool", "argus", 1));

		List<Run> all = repository.list(10, null, null, null);
		assertThat(all).hasSize(2);
		assertThat(all.getFirst().systemPrompt()).isNull();

		assertThat(repository.list(10, "no-tool", null, null)).extracting(Run::id).containsExactly(b);
		assertThat(repository.list(10, null, "forge", null)).extracting(Run::id).containsExactly(a);
		assertThat(repository.list(10, null, null, batch)).extracting(Run::id).containsExactly(b);
		assertThat(repository.countByAgent("argus")).isEqualTo(1);
		assertThat(repository.countByModel("local")).isEqualTo(2);
		assertThat(repository.count()).isEqualTo(2);
	}

	/** The executor's compare-and-set: a cancelled run is not revived. */
	@Test
	void queuedRunsCanBeStartedOnceOrCancelled() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID batch = UUID.randomUUID();
		repository.create(queued(a, batch, "no-tool", "argus", 1));
		repository.create(queued(b, batch, "no-tool", "argus", 1));

		assertThat(repository.markRunning(a, Instant.now())).isTrue();
		assertThat(repository.markRunning(a, Instant.now())).isFalse();
		assertThat(repository.cancel(a)).isFalse();
		assertThat(repository.find(a).orElseThrow().status()).isEqualTo(RunStatus.RUNNING);

		assertThat(repository.cancelBatch(batch)).isEqualTo(1);
		assertThat(repository.find(b).orElseThrow().status()).isEqualTo(RunStatus.CANCELLED);
		assertThat(repository.find(b).orElseThrow().finishedAt()).isNotNull();
		assertThat(repository.markRunning(b, Instant.now())).isFalse();
	}

	@Test
	void matrixRowsAreOnlyTaskRunsOfTheAgentOldestFirst() {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		UUID adHoc = UUID.randomUUID();
		repository.create(queued(first, null, "no-tool", "forge", 1));
		repository.create(queued(second, null, "time-and-division", "forge", 2));
		repository.create(queued(adHoc, null, null, "forge", 2));
		repository.create(queued(UUID.randomUUID(), null, "no-tool", "argus", 1));

		List<Run> rows = repository.matrixRows("forge");
		assertThat(rows).extracting(Run::id).containsExactly(first, second);
	}
}
