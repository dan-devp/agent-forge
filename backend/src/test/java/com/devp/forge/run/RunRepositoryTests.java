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
		return new Run(id, "time-and-division", 1, "forge", 2, "local", 1, "mock-local-7b", 0.7, "Du bist Forge.",
				"Wie spaet ist es?", List.of("currentTime", "divide"), RunStatus.RUNNING, null, null, 0, null, null,
				null, Instant.now(), null, List.of(), List.of());
	}

	@Test
	void writesAndReadsARun() {
		UUID id = UUID.randomUUID();
		repository.start(started(id));

		Run read = repository.find(id).orElseThrow();
		assertThat(read.agentId()).isEqualTo("forge");
		assertThat(read.agentVersion()).isEqualTo(2);
		assertThat(read.modelId()).isEqualTo("local");
		assertThat(read.temperature()).isEqualTo(0.7);
		assertThat(read.systemPrompt()).isEqualTo("Du bist Forge.");
		assertThat(read.tools()).containsExactly("currentTime", "divide");
		assertThat(read.status()).isEqualTo(RunStatus.RUNNING);
		assertThat(read.passed()).isNull();
		assertThat(read.trace()).isEmpty();
		assertThat(read.evals()).isEmpty();
	}

	@Test
	void appendsStepsAndEvalsInOrder() {
		UUID id = UUID.randomUUID();
		repository.start(started(id));

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
		repository.start(started(a));
		Run other = started(b);
		repository.start(new Run(b, "no-tool", 1, "argus", 1, other.modelId(), 1, other.modelName(), 0.7, "x", "y",
				List.of(), RunStatus.RUNNING, null, null, 0, null, null, null, Instant.now(), null, List.of(),
				List.of()));

		List<Run> all = repository.list(10, null, null);
		assertThat(all).hasSize(2);
		assertThat(all.getFirst().systemPrompt()).isNull();

		assertThat(repository.list(10, "no-tool", null)).extracting(Run::id).containsExactly(b);
		assertThat(repository.list(10, null, "forge")).extracting(Run::id).containsExactly(a);
		assertThat(repository.countByAgent("argus")).isEqualTo(1);
		assertThat(repository.countByModel("local")).isEqualTo(2);
	}
}
