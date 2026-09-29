package com.devp.forge.run;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import com.devp.forge.agent.AgentRepository;
import com.devp.forge.config.RunProperties;
import com.devp.forge.run.RunQueue.Batch;
import com.devp.forge.run.RunQueue.Matrix;
import com.devp.forge.support.PostgresTestcontainer;
import com.devp.forge.task.TaskRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Queueing and the matrix, without executing: the executor here collects the work instead
 * of running it, so no run ever reaches the (dead) endpoint.
 */
@SpringBootTest
@Import(PostgresTestcontainer.class)
class RunQueueTests {

	@Autowired
	private RunService service;

	@Autowired
	private RunRepository runs;

	@Autowired
	private AgentRepository agents;

	@Autowired
	private TaskRepository tasks;

	@Autowired
	private RunProperties properties;

	private final List<Runnable> collected = new ArrayList<>();
	private RunQueue queue;

	@BeforeEach
	void setUp() {
		runs.deleteAll();
		collected.clear();
		Executor collecting = collected::add;
		queue = new RunQueue(service, runs, agents, tasks, properties, collecting);
	}

	@Test
	void enqueueCreatesQueuedRunsWithPinnedVersionsAndHandsThemToTheExecutor() {
		Batch batch = queue.enqueue("forge", null, null, 2);

		int taskCount = tasks.list().size();
		assertThat(batch.runIds()).hasSize(taskCount * 2);
		assertThat(batch.agentVersion()).isEqualTo(agents.get("forge").version());
		assertThat(collected).hasSize(1);

		List<Run> queued = runs.list(100, null, null, batch.id());
		assertThat(queued).hasSize(taskCount * 2);
		assertThat(queued).allMatch(r -> r.status() == RunStatus.QUEUED);
		assertThat(queued).allMatch(r -> r.startedAt() == null && r.queuedAt() != null);
		assertThat(queued).extracting(Run::taskId).contains("no-tool", "time-and-division");
	}

	@Test
	void cancelBatchWithdrawsEverythingStillQueued() {
		Batch batch = queue.enqueue("argus", null, List.of("no-tool"), 3);

		assertThat(queue.cancelBatch(batch.id())).isEqualTo(3);
		assertThat(runs.list(100, null, null, batch.id())).allMatch(r -> r.status() == RunStatus.CANCELLED);
		// Running the collected work now finds nothing to start.
		collected.forEach(Runnable::run);
		assertThat(runs.list(100, null, null, batch.id())).allMatch(r -> r.status() == RunStatus.CANCELLED);
	}

	@Test
	void refusesOversizedBatches() {
		assertThatThrownBy(() -> queue.enqueue("forge", null, null, properties.getMaxRepetitions() + 1))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("repetitions");
		assertThatThrownBy(() -> queue.enqueue("forge", null, List.of(), 0))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void matrixHasARowPerAgentVersionAndAColumnPerTask() {
		queue.enqueue("forge", null, List.of("no-tool"), 2);

		Matrix matrix = queue.matrix("forge");

		assertThat(matrix.tasks()).contains("no-tool", "time-and-division");
		assertThat(matrix.rows()).extracting(RunQueue.MatrixRow::agentVersion)
				.contains(agents.get("forge").version());
		RunQueue.MatrixRow current = matrix.rows().stream()
				.filter(r -> r.agentVersion() == matrix.currentVersion()).findFirst().orElseThrow();
		RunQueue.Pair noTool = current.pairs().stream().filter(c -> c.taskId().equals("no-tool")).findFirst()
				.orElseThrow();
		RunQueue.Pair other = current.pairs().stream().filter(c -> c.taskId().equals("time-and-division")).findFirst()
				.orElseThrow();
		assertThat(noTool.runs()).hasSize(2);
		assertThat(noTool.taskVersions()).containsExactly(tasks.get("no-tool").version());
		assertThat(other.runs()).isEmpty();
	}

	@Test
	void seriesRunsCarryTheirVerdictsSoTheMatrixTellsWhy() {
		Batch batch = queue.enqueue("forge", null, List.of("no-tool"), 1);
		UUID runId = batch.runIds().getFirst();
		String longRationale = "x ".repeat(400);
		runs.addEval(runId, new RunEval(1, "tool-not-called", false, longRationale, null, null, null, Instant.now()));
		runs.addEval(runId, new RunEval(2, "contains", true, "gefunden", null, null, null, Instant.now()));

		RunQueue.SeriesRun run = queue.matrix("forge").rows().stream()
				.flatMap(r -> r.pairs().stream())
				.flatMap(p -> p.runs().stream())
				.filter(r -> r.id().equals(runId)).findFirst().orElseThrow();

		assertThat(run.at()).isNotNull();
		assertThat(run.verdicts()).extracting(RunQueue.Verdict::type).containsExactly("tool-not-called", "contains");
		assertThat(run.verdicts()).extracting(RunQueue.Verdict::passed).containsExactly(false, true);
		assertThat(run.verdicts().getFirst().rationale()).hasSizeLessThanOrEqualTo(240).endsWith("…");
	}
}
