package com.devp.agent.plan;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.devp.agent.agent.AgentRepository;
import com.devp.agent.ai.ChatClientRegistry;
import com.devp.agent.config.AgentProperties;
import com.devp.agent.experiment.Experiment;
import com.devp.agent.experiment.ExperimentService;
import com.devp.agent.profile.ProfileRepository;
import com.devp.agent.task.TaskRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Plans: save a comparison, start it as often as you want.
 *
 * <p>Everything a plan names is verified when it is saved, not when it runs — a plan that
 * points at a deleted task must fail while someone is looking at it, not three trainings into an
 * execution.
 */
@Service
public class PlanService {

	private static final Logger log = LoggerFactory.getLogger(PlanService.class);

	private final PlanRepository plans;
	private final ExperimentService experiments;
	private final TaskRepository tasks;
	private final AgentRepository agents;
	private final ProfileRepository profiles;
	private final ChatClientRegistry providers;
	private final AgentProperties properties;

	public PlanService(PlanRepository plans, ExperimentService experiments, TaskRepository tasks,
			AgentRepository agents, ProfileRepository profiles, ChatClientRegistry providers,
			AgentProperties properties) {
		this.plans = plans;
		this.experiments = experiments;
		this.tasks = tasks;
		this.agents = agents;
		this.profiles = profiles;
		this.providers = providers;
		this.properties = properties;
	}

	public Plan create(String name, String description, String taskId, String agentId, String providerId,
			List<String> profileIds, boolean includeBaseline, int repetitions) {
		Instant now = Instant.now();
		Plan plan = verified(new Plan(UUID.randomUUID(), name(name), description, taskId, agentId, providerId,
				distinct(profileIds), includeBaseline, repetitions, now, now));
		plans.create(plan);
		log.info("Plan {} created: '{}' on task {}, {} profile(s), x{}", plan.id(), plan.name(), plan.taskId(),
				plan.profiles().size(), plan.repetitions());
		return plan;
	}

	public Plan update(UUID id, String name, String description, String taskId, String agentId, String providerId,
			List<String> profileIds, boolean includeBaseline, int repetitions) {
		Plan existing = require(id);
		Plan plan = verified(new Plan(existing.id(), name(name), description, taskId, agentId, providerId,
				distinct(profileIds), includeBaseline, repetitions, existing.createdAt(), Instant.now()));
		plans.update(plan);
		return plan;
	}

	/** One execution of the plan. Resolution of agent, SOUL and provider happens there, once. */
	public Experiment start(UUID id) {
		Plan plan = require(id);
		return experiments.start(plan.id(), plan.name(), plan.taskId(), plan.repetitions(), plan.agentId(),
				plan.providerId(), null, plan.profiles(), plan.includeBaseline());
	}

	public void delete(UUID id) {
		require(id);
		plans.delete(id);
	}

	public List<Plan> list() {
		return plans.list();
	}

	public Optional<Plan> find(UUID id) {
		return plans.find(id);
	}

	private Plan require(UUID id) {
		return plans.find(id).orElseThrow(() -> new UnknownPlanException("Unknown plan '%s'.".formatted(id)));
	}

	/**
	 * Names everything the plan refers to against the catalogues. Unknown ids throw the same
	 * exceptions as a run would, so the message is identical wherever it surfaces.
	 */
	private Plan verified(Plan plan) {
		tasks.resolve(plan.taskId());
		if (plan.agentId() != null && !plan.agentId().isBlank()) {
			agents.resolve(plan.agentId());
		}
		if (plan.providerId() != null && !plan.providerId().isBlank()) {
			providers.resolve(plan.providerId());
		}
		plan.profiles().forEach(profiles::resolve);

		if (plan.repetitions() < 1 || plan.repetitions() > properties.getMaxTrainingRepetitions()) {
			throw new IllegalArgumentException("repetitions must be between 1 and %d."
					.formatted(properties.getMaxTrainingRepetitions()));
		}
		if (plan.profiles().isEmpty() && !plan.includeBaseline()) {
			throw new IllegalArgumentException("A plan needs at least one profile or the baseline training.");
		}
		return plan;
	}

	private static List<String> distinct(List<String> ids) {
		return ids == null ? List.of()
				: List.copyOf(new LinkedHashSet<>(ids.stream().filter(id -> id != null && !id.isBlank()).toList()));
	}

	private static String name(String name) {
		return name == null || name.isBlank() ? "Unbenannter Plan" : name.strip();
	}

	/** The requested plan id does not exist. */
	public static class UnknownPlanException extends RuntimeException {

		public UnknownPlanException(String message) {
			super(message);
		}
	}
}
