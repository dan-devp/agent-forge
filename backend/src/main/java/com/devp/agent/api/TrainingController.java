package com.devp.agent.api;

import java.util.List;
import java.util.UUID;

import com.devp.agent.run.AgentRun;
import com.devp.agent.training.Training;
import com.devp.agent.training.TrainingMemory;
import com.devp.agent.training.TrainingService;
import com.devp.agent.training.TrainingStatistics;

import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trainings: one task, one profile, N runs in order, statistics and memory.
 *
 * <p>{@code POST} answers 202 at once with the created training; the runs happen in the
 * background. Poll {@code GET /api/trainings/{id}} until {@code status} is no longer RUNNING.
 */
@RestController
@RequestMapping("/api/trainings")
@Validated
public class TrainingController {

	private final TrainingService trainings;

	public TrainingController(TrainingService trainings) {
		this.trainings = trainings;
	}

	@PostMapping
	public ResponseEntity<Training> start(@Validated @RequestBody TrainingRequest request) {
		Training created = trainings.start(request.task(), request.repetitions() == null ? 5 : request.repetitions(),
				request.agent(), request.provider(), request.soul(), request.profile());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(created);
	}

	/** All trainings, or those of one task — the ones that get compared. */
	@GetMapping
	public List<Training> list(@RequestParam(required = false) String task) {
		return trainings.list(task);
	}

	/** Stops a running training. Takes effect before the next run, not during one. */
	@PostMapping("/{id}/cancel")
	public Training cancel(@PathVariable UUID id) {
		trainings.cancel(id);
		return trainings.find(id)
				.orElseThrow(() -> new UnknownTrainingException("Unknown training '%s'.".formatted(id)));
	}

	/** Training, statistics computed now, its runs without traces, and what it remembers. */
	@GetMapping("/{id}")
	public TrainingDetail detail(@PathVariable UUID id) {
		Training t = trainings.find(id)
				.orElseThrow(() -> new UnknownTrainingException("Unknown training '%s'.".formatted(id)));
		List<AgentRun> runs = trainings.runsOf(id);
		return new TrainingDetail(t, trainings.statistics(t, runs), runs, trainings.memory(id));
	}

	/** Only a named task can be trained on; {@code repetitions} defaults to 5. */
	public record TrainingRequest(
			@NotBlank(message = "task is required — only a named task can be trained on")
			String task,

			Integer repetitions,

			String agent,

			String provider,

			String soul,

			String profile) {
	}

	public record TrainingDetail(Training training, TrainingStatistics statistics, List<AgentRun> runs,
			List<TrainingMemory> memory) {
	}

	/** The requested training id does not exist. */
	public static class UnknownTrainingException extends RuntimeException {
		public UnknownTrainingException(String message) {
			super(message);
		}
	}
}
