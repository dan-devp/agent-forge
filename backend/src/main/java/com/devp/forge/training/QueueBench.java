package com.devp.forge.training;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.devp.forge.config.TrainingProperties;
import com.devp.forge.run.Run;
import com.devp.forge.run.RunEval;
import com.devp.forge.run.RunQueue;
import com.devp.forge.run.RunQueue.Batch;
import com.devp.forge.run.RunRepository;
import com.devp.forge.run.RunStatus;
import com.devp.forge.run.StepType;

import org.springframework.stereotype.Component;

/**
 * The real bench: one batch through the ordinary run queue, so a training's runs are runs
 * like any other — in the matrix, in the history, with trace and verdicts.
 */
@Component
public class QueueBench implements Bench {

	/** Per train task; more is noise for the coach and cost for the context. */
	private static final int FAILURES_PER_TASK = 2;
	private static final int CLIP = 600;

	private final RunQueue queue;
	private final RunRepository runs;
	private final TrainingProperties properties;

	public QueueBench(RunQueue queue, RunRepository runs, TrainingProperties properties) {
		this.queue = queue;
		this.runs = runs;
		this.properties = properties;
	}

	@Override
	public Measurement measure(String agentId, int agentVersion, List<String> train, List<String> holdout,
			int repetitions, BooleanSupplier stop) {
		List<String> all = Stream.concat(train.stream(), holdout.stream()).toList();
		Batch batch = queue.enqueue(agentId, agentVersion, all, repetitions);

		boolean stopped = false;
		List<Run> done;
		while (true) {
			done = runs.list(Integer.MAX_VALUE, null, null, batch.id());
			if (done.stream().allMatch(r -> r.status().isFinal())) {
				break;
			}
			if (!stopped && stop.getAsBoolean()) {
				// Withdraw what still waits; the one running is paid for and finishes.
				queue.cancelBatch(batch.id());
				stopped = true;
			}
			sleep();
		}

		List<Run> counted = done.stream().filter(r -> r.status() != RunStatus.CANCELLED).toList();
		Map<String, List<Run>> byTask = counted.stream().collect(Collectors.groupingBy(Run::taskId));
		List<TaskScore> tasks = new ArrayList<>();
		int tp = 0, tt = 0, hp = 0, ht = 0;
		for (String taskId : all) {
			List<Run> series = byTask.getOrDefault(taskId, List.of());
			int passed = (int) series.stream().filter(r -> Boolean.TRUE.equals(r.passed())).count();
			boolean isHoldout = holdout.contains(taskId);
			tasks.add(new TaskScore(taskId, isHoldout, passed, series.size()));
			if (isHoldout) {
				hp += passed;
				ht += series.size();
			}
			else {
				tp += passed;
				tt += series.size();
			}
		}

		List<FailedRun> failures = new ArrayList<>();
		for (String taskId : train) {
			byTask.getOrDefault(taskId, List.of()).stream()
					.filter(r -> !Boolean.TRUE.equals(r.passed()))
					.limit(FAILURES_PER_TASK)
					.map(r -> runs.find(r.id()).orElse(r))
					.map(QueueBench::failure)
					.forEach(failures::add);
		}

		return new Measurement(batch.id(), new Score(tp, tt, hp, ht), tasks, failures, runs.tokensOfBatch(batch.id()),
				stopped);
	}

	static FailedRun failure(Run r) {
		List<String> tools = r.trace().stream()
				.filter(s -> s.type() == StepType.TOOL)
				.map(s -> "%s(%s) -> %s".formatted(s.name(), clip(s.input()),
						s.error() != null ? "Fehler: " + clip(s.error()) : clip(s.output())))
				.toList();
		Function<RunEval, String> verdict = e -> (e.subject() != null ? e.subject() : e.type()) + ": "
				+ clip(e.rationale());
		return new FailedRun(r.taskId(), r.id(), r.status().name(), clip(r.input()), clip(r.answer()), clip(r.error()),
				tools, r.evals().stream().filter(e -> !e.passed()).map(verdict).toList());
	}

	private static String clip(String text) {
		if (text == null) return null;
		String flat = text.strip();
		return flat.length() <= CLIP ? flat : flat.substring(0, CLIP - 3) + "...";
	}

	private void sleep() {
		try {
			Thread.sleep(properties.getPollMs());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Measurement interrupted", ex);
		}
	}
}
