package com.devp.agent.api;

import java.util.List;
import java.util.UUID;

import com.devp.agent.experiment.Experiment;
import com.devp.agent.experiment.ExperimentService;
import com.devp.agent.experiment.ExperimentService.TrainingSummary;

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
 * Experiments: one agent on one task under several profiles, training after training.
 *
 * <p>{@code POST} answers 202 at once with the created experiment; the trainings are trained in
 * the background, one at a time. Poll {@code GET /api/experiments/{id}} until {@code status}
 * is no longer RUNNING — it returns every training with its statistics in one response, so the
 * comparison table does not have to fetch each training separately.
 */
@RestController
@RequestMapping("/api/experiments")
@Validated
public class ExperimentController {

	private final ExperimentService experiments;

	public ExperimentController(ExperimentService experiments) {
		this.experiments = experiments;
	}

	@PostMapping
	public ResponseEntity<Experiment> start(@Validated @RequestBody ExperimentRequest request) {
		Experiment created = experiments.start(request.name(), request.task(),
				request.repetitions() == null ? 5 : request.repetitions(), request.agent(), request.provider(),
				request.soul(), request.profiles(), request.includeBaseline() == null || request.includeBaseline());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(created);
	}

	@GetMapping
	public List<Experiment> list(@RequestParam(required = false) String task) {
		return experiments.list(task);
	}

	/**
	 * Stops a running experiment. Queued trainings end at once, the training in progress after its
	 * current run — an answer already paid for stays a data point.
	 */
	@PostMapping("/{id}/cancel")
	public Experiment cancel(@PathVariable UUID id) {
		experiments.cancel(id);
		return experiments.find(id)
				.orElseThrow(() -> new UnknownExperimentException("Unknown experiment '%s'.".formatted(id)));
	}

	/** The experiment with all its trainings and their statistics — the comparison in one request. */
	@GetMapping("/{id}")
	public ExperimentDetail detail(@PathVariable UUID id) {
		Experiment experiment = experiments.find(id)
				.orElseThrow(() -> new UnknownExperimentException("Unknown experiment '%s'.".formatted(id)));
		return new ExperimentDetail(experiment, experiments.trainings(id));
	}

	/**
	 * @param profiles       profiles to compare; the baseline training is added unless excluded
	 * @param includeBaseline default true — without a reference training nothing is measured
	 */
	public record ExperimentRequest(
			String name,

			@NotBlank(message = "task is required — only a named task can be trained on")
			String task,

			Integer repetitions,

			String agent,

			String provider,

			String soul,

			List<String> profiles,

			Boolean includeBaseline) {
	}

	public record ExperimentDetail(Experiment experiment, List<TrainingSummary> trainings) {
	}

	/** The requested experiment id does not exist. */
	public static class UnknownExperimentException extends RuntimeException {

		public UnknownExperimentException(String message) {
			super(message);
		}
	}
}
