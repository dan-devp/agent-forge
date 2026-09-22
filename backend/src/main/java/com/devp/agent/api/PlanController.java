package com.devp.agent.api;

import java.util.List;
import java.util.UUID;

import com.devp.agent.experiment.Experiment;
import com.devp.agent.experiment.ExperimentService;
import com.devp.agent.plan.Plan;
import com.devp.agent.plan.PlanService;

import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plans: the saved comparison. Saving it does not run anything — {@code POST /{id}/start}
 * does, as often as asked, and every start is its own experiment.
 */
@RestController
@RequestMapping("/api/plans")
@Validated
public class PlanController {

	private final PlanService plans;
	private final ExperimentService experiments;

	public PlanController(PlanService plans, ExperimentService experiments) {
		this.plans = plans;
		this.experiments = experiments;
	}

	@GetMapping
	public List<Plan> list() {
		return plans.list();
	}

	/** The plan with its executions, newest first — the time series over one configuration. */
	@GetMapping("/{id}")
	public PlanDetail detail(@PathVariable UUID id) {
		Plan plan = plans.find(id)
				.orElseThrow(() -> new PlanService.UnknownPlanException("Unknown plan '%s'.".formatted(id)));
		return new PlanDetail(plan, experiments.listForPlan(id));
	}

	@PostMapping
	public ResponseEntity<Plan> create(@Validated @RequestBody PlanRequest request) {
		Plan created = plans.create(request.name(), request.description(), request.task(), request.agent(),
				request.provider(), request.profiles(), request.includeBaseline() == null || request.includeBaseline(),
				request.repetitions() == null ? 5 : request.repetitions());
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PutMapping("/{id}")
	public Plan update(@PathVariable UUID id, @Validated @RequestBody PlanRequest request) {
		return plans.update(id, request.name(), request.description(), request.task(), request.agent(),
				request.provider(), request.profiles(), request.includeBaseline() == null || request.includeBaseline(),
				request.repetitions() == null ? 5 : request.repetitions());
	}

	/** Runs the plan. Answers 202 with the created experiment; the trainings train in the background. */
	@PostMapping("/{id}/start")
	public ResponseEntity<Experiment> start(@PathVariable UUID id) {
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(plans.start(id));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		plans.delete(id);
		return ResponseEntity.noContent().build();
	}

	public record PlanRequest(
			String name,

			String description,

			@NotBlank(message = "task is required — only a named task can be trained on")
			String task,

			String agent,

			String provider,

			List<String> profiles,

			Boolean includeBaseline,

			Integer repetitions) {
	}

	public record PlanDetail(Plan plan, List<Experiment> experiments) {
	}
}
