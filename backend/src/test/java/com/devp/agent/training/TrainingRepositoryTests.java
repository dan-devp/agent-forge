package com.devp.agent.training;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.devp.agent.run.AgentRun;
import com.devp.agent.run.RunRepository;
import com.devp.agent.run.RunStatus;
import com.devp.agent.run.RunStep;
import com.devp.agent.run.StepType;
import com.devp.agent.strategy.MemoryStore;
import com.devp.agent.support.PostgresTestcontainer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestcontainer.class)
class TrainingRepositoryTests {

	@Autowired
	private TrainingRepository trainings;

	@Autowired
	private RunRepository runs;

	@BeforeEach
	void clear() {
		runs.deleteAll();
		trainings.deleteAll();
	}

	private Training created(String taskId) {
		return new Training(UUID.randomUUID(), null, taskId, 1, "forge", 1, "forge", "baseline", "local", 3,
				TrainingStatus.RUNNING, 0, null, Instant.now(), null);
	}

	private UUID runIn(UUID trainingId, Instant startedAt) {
		UUID id = UUID.randomUUID();
		runs.start(new AgentRun(id, "goal", "time-and-division", 1, "forge", 1, "forge", 1, "baseline", 1, trainingId,
				"local", "m", 0.7, "prompt", List.of(), RunStatus.RUNNING, null, null, 0, null, null, startedAt, null,
				List.of(), List.of()));
		return id;
	}

	@Test
	void createsProgressesAndFinishes() {
		Training t = created("time-and-division");
		trainings.create(t);

		trainings.progress(t.id(), 2);
		assertThat(trainings.find(t.id()).orElseThrow().completed()).isEqualTo(2);

		trainings.finish(t.withProgress(3).finished(TrainingStatus.DONE, null, Instant.now()));
		Training done = trainings.find(t.id()).orElseThrow();
		assertThat(done.status()).isEqualTo(TrainingStatus.DONE);
		assertThat(done.completed()).isEqualTo(3);
		assertThat(done.finishedAt()).isNotNull();
		assertThat(done.soulId()).isEqualTo("forge");
	}

	/** Runs hang from the training and come back in execution order. */
	@Test
	void linksRunsInExecutionOrder() {
		Training t = created("time-and-division");
		trainings.create(t);
		Instant now = Instant.now();
		UUID first = runIn(t.id(), now.minusSeconds(20));
		UUID second = runIn(t.id(), now.minusSeconds(10));
		runIn(null, now); // a single run outside any training

		assertThat(trainings.runIds(t.id())).containsExactly(first, second);
		assertThat(runs.find(first).orElseThrow().trainingId()).isEqualTo(t.id());
	}

	@Test
	void countsToolCallsPerRun() {
		Training t = created("time-and-division");
		trainings.create(t);
		UUID withTools = runIn(t.id(), Instant.now().minusSeconds(5));
		UUID withoutTools = runIn(t.id(), Instant.now());
		runs.addStep(withTools, new RunStep(1, StepType.MODEL, "m", null, "x", null, null, null, Instant.now()));
		runs.addStep(withTools, new RunStep(2, StepType.TOOL, "divide", "{}", "x", null, 1L, null, Instant.now()));
		runs.addStep(withTools, new RunStep(3, StepType.TOOL, "divide", "{}", "x", null, 1L, null, Instant.now()));

		assertThat(trainings.toolCallsByRun(t.id()))
				.containsEntry(withTools, 2)
				.containsEntry(withoutTools, 0);
	}

	/** The trainings of one task are the set that gets compared. */
	@Test
	void listsPerTaskNewestFirst() {
		Training a = created("time-and-division");
		trainings.create(a);
		Training b = new Training(UUID.randomUUID(), null, "time-and-division", 1, "forge", 1, "forge", "reflexion",
				"local", 3, TrainingStatus.RUNNING, 0, null, Instant.now().plusSeconds(1), null);
		trainings.create(b);
		trainings.create(created("no-tool"));

		assertThat(trainings.listForTask("time-and-division", 10)).extracting(Training::id).containsExactly(b.id(), a.id());
		assertThat(trainings.list(10)).hasSize(3);
	}

	/**
	 * Memory is scoped to training and strategy and attributed to the run — training A never
	 * sees what training B learned, and every entry can be traced to where it came from.
	 */
	@Test
	void memoryIsScopedAndAttributed() {
		Training a = created("time-and-division");
		Training b = created("time-and-division");
		trainings.create(a);
		trainings.create(b);
		UUID runA1 = runIn(a.id(), Instant.now().minusSeconds(10));
		UUID runA2 = runIn(a.id(), Instant.now());

		MemoryStore storeA1 = new TrainingMemoryStore(trainings, a.id(), runA1);
		storeA1.write("reflexion", "lesson", "Tools nutzen, nicht raten.");
		storeA1.write("reflexion", "note", "Zweiter Eintrag.");
		storeA1.write("other", "lesson", "Von einer anderen Strategie.");
		storeA1.write("reflexion", "lesson", "   "); // blank is not a lesson

		MemoryStore storeA2 = new TrainingMemoryStore(trainings, a.id(), runA2);
		assertThat(storeA2.persistent()).isTrue();
		assertThat(storeA2.read("reflexion")).extracting(TrainingMemory::content)
				.containsExactly("Tools nutzen, nicht raten.", "Zweiter Eintrag.");
		assertThat(storeA2.read("reflexion", "lesson")).extracting(TrainingMemory::content)
				.containsExactly("Tools nutzen, nicht raten.");
		assertThat(storeA2.read("reflexion").getFirst().runId()).isEqualTo(runA1);

		MemoryStore storeB = new TrainingMemoryStore(trainings, b.id(), null);
		assertThat(storeB.read("reflexion")).isEmpty();

		assertThat(trainings.memory(a.id())).hasSize(3);
	}

	@Test
	void unknownTrainingIsEmpty() {
		assertThat(trainings.find(UUID.randomUUID())).isEmpty();
	}
}
