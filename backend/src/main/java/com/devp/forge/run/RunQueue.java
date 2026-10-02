package com.devp.forge.run;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Executor;

import com.devp.forge.agent.Agent;
import com.devp.forge.agent.AgentRepository;
import com.devp.forge.config.RunProperties;
import com.devp.forge.task.Task;
import com.devp.forge.task.TaskRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Queues batches and executes their runs one after another.
 *
 * <p>A batch is agent version x tasks x repetitions. All runs are created as QUEUED first
 * with their versions pinned, then handed to the single-thread executor. Cancelling flips
 * QUEUED runs to CANCELLED; the executor checks with a compare-and-set before each start,
 * so a cancelled run is never revived. A running model call is not interrupted — it is
 * paid for, so its answer is still recorded.
 */
@Service
public class RunQueue {

	private static final Logger log = LoggerFactory.getLogger(RunQueue.class);

	private final RunService service;
	private final RunRepository runs;
	private final AgentRepository agents;
	private final TaskRepository tasks;
	private final RunProperties properties;
	private final Executor executor;

	public RunQueue(RunService service, RunRepository runs, AgentRepository agents, TaskRepository tasks,
			RunProperties properties, @Qualifier("runExecutor") Executor executor) {
		this.service = service;
		this.runs = runs;
		this.agents = agents;
		this.tasks = tasks;
		this.properties = properties;
		this.executor = executor;
	}

	/**
	 * @param taskIds      tasks to run, or {@code null}/empty for every current task
	 * @param agentVersion explicit version, or {@code null} for the current one
	 */
	public Batch enqueue(String agentId, Integer agentVersion, List<String> taskIds, int repetitions) {
		if (repetitions < 1 || repetitions > properties.getMaxRepetitions()) {
			throw new IllegalArgumentException(
					"'repetitions' must be between 1 and %d.".formatted(properties.getMaxRepetitions()));
		}
		Agent agent = service.resolveAgent(agentId, agentVersion);
		List<Task> chosen = taskIds == null || taskIds.isEmpty()
				? tasks.list()
				: taskIds.stream().map(tasks::get).toList();
		if (chosen.isEmpty()) {
			throw new IllegalArgumentException("No tasks to run.");
		}
		if (chosen.size() * repetitions > properties.getMaxBatchRuns()) {
			throw new IllegalArgumentException("A batch may hold at most %d runs (%d tasks x %d repetitions = %d)."
					.formatted(properties.getMaxBatchRuns(), chosen.size(), repetitions, chosen.size() * repetitions));
		}

		UUID batchId = UUID.randomUUID();
		List<UUID> ids = new ArrayList<>();
		// Repetition outer, task inner: the tasks interleave, so a slow drift at the endpoint
		// hits every task alike instead of one task at the end.
		for (int rep = 0; rep < repetitions; rep++) {
			for (Task task : chosen) {
				Run queued = service.prepare(agent, task, null, batchId, null);
				runs.create(queued);
				ids.add(queued.id());
			}
		}
		log.info("Batch {} queued: agent {} v{}, {} task(s) x {} = {} run(s)", batchId, agent.id(), agent.version(),
				chosen.size(), repetitions, ids.size());

		executor.execute(() -> ids.forEach(this::runQueued));
		return new Batch(batchId, agent.id(), agent.version(), chosen.stream().map(Task::id).toList(), repetitions,
				ids);
	}

	private void runQueued(UUID id) {
		if (!runs.markRunning(id, Instant.now())) {
			log.info("Run {} skipped: no longer queued", id);
			return;
		}
		Run run = runs.find(id).orElseThrow().withStatus(RunStatus.RUNNING, Instant.now());
		try {
			service.execute(run);
		}
		catch (RuntimeException ex) {
			// Already recorded as FAILED by the service; the batch goes on.
			log.warn("Run {} in batch {} failed: {}", id, run.batchId(), ex.getMessage());
		}
	}

	public boolean cancel(UUID runId) {
		return runs.cancel(runId);
	}

	public int cancelBatch(UUID batchId) {
		return runs.cancelBatch(batchId);
	}

	/**
	 * The picture: agent versions as rows, tasks as columns, each cell a pair with its run series.
	 * Rows include every known version of the agent, so a version without runs shows as
	 * empty rather than missing. Computed from the runs on every call; nothing is stored.
	 */
	public Matrix matrix(String agentId) {
		Agent current = agents.get(agentId);
		List<Run> rows = runs.matrixRows(agentId);
		Map<UUID, List<RunEval>> verdicts = runs.evalsByAgent(agentId);

		TreeMap<Integer, Map<String, List<Run>>> byVersion = new TreeMap<>();
		agents.versions(agentId).forEach(a -> byVersion.put(a.version(), new LinkedHashMap<>()));
		for (Run run : rows) {
			byVersion.computeIfAbsent(run.agentVersion(), v -> new LinkedHashMap<>())
					.computeIfAbsent(run.taskId(), t -> new ArrayList<>())
					.add(run);
		}

		// Columns: current tasks first, in store order, then any task only runs still know.
		List<String> columns = new ArrayList<>(tasks.list().stream().map(Task::id).toList());
		rows.stream().map(Run::taskId).filter(t -> !columns.contains(t)).forEach(columns::add);

		List<MatrixRow> matrixRows = byVersion.entrySet().stream().map(e -> {
			List<Pair> pairs = columns.stream().map(taskId -> {
				List<Run> series = e.getValue().getOrDefault(taskId, List.of());
				return new Pair(taskId,
						series.stream().map(Run::taskVersion).distinct().sorted().toList(),
						series.stream().map(Run::modelVersion).distinct().sorted().toList(),
						series.stream().map(r -> SeriesRun.of(r, verdicts.getOrDefault(r.id(), List.of()))).toList());
			}).toList();
			return new MatrixRow(e.getKey(), pairs);
		}).toList();

		return new Matrix(agentId, current.version(), current.modelId(), columns, matrixRows);
	}

	public record Batch(UUID id, String agentId, int agentVersion, List<String> taskIds, int repetitions,
			List<UUID> runIds) {
	}

	public record Matrix(String agentId, int currentVersion, String modelId, List<String> tasks,
			List<MatrixRow> rows) {
	}

	public record MatrixRow(int agentVersion, List<Pair> pairs) {
	}

	/**
	 * Agent version x task, the unit that is compared; {@code runs} is its run series.
	 * {@code taskVersions} and {@code modelVersions} list more than one entry when the series mixes versions.
	 */
	public record Pair(String taskId, List<Integer> taskVersions, List<Integer> modelVersions, List<SeriesRun> runs) {
	}

	/**
	 * One run of a series, enough to see why it failed without opening it: when, the start of
	 * the answer (or the error), and every verdict. {@code at} is the start, or the queueing time
	 * while the run still waits.
	 */
	public record SeriesRun(UUID id, UUID batchId, RunStatus status, Boolean passed, Integer taskVersion,
			Integer totalTokens, Long durationMs, Instant at, String preview, List<Verdict> verdicts) {

		static SeriesRun of(Run r, List<RunEval> evals) {
			return new SeriesRun(r.id(), r.batchId(), r.status(), r.passed(), r.taskVersion(), r.totalTokens(),
					r.durationMs(), r.startedAt() != null ? r.startedAt() : r.queuedAt(),
					clip(r.answer() != null ? r.answer() : r.error(), PREVIEW),
					evals.stream().map(e -> new Verdict(e.index(), e.type(), e.subject(), e.passed(),
							clip(e.rationale(), RATIONALE)))
							.toList());
		}
	}

	/** A verdict as the matrix shows it; the judge's cost stays on the run detail. */
	public record Verdict(int index, String type, String subject, boolean passed, String rationale) {
	}

	// The matrix carries every run of an agent; full answers and judge rationales belong to the detail.
	private static final int PREVIEW = 160;
	private static final int RATIONALE = 240;

	private static String clip(String text, int max) {
		if (text == null) return null;
		String flat = text.replaceAll("\\s+", " ").strip();
		return flat.length() <= max ? flat : flat.substring(0, max - 1) + "\u2026";
	}
}
