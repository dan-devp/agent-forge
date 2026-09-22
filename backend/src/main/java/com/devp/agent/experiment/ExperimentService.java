package com.devp.agent.experiment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.devp.agent.agent.Agent;
import com.devp.agent.agent.AgentRepository;
import com.devp.agent.ai.ChatClientRegistry;
import com.devp.agent.config.AgentProperties;
import com.devp.agent.config.ProfileProperties;
import com.devp.agent.profile.ProfileRepository;
import com.devp.agent.task.Task;
import com.devp.agent.task.TaskRepository;
import com.devp.agent.training.Training;
import com.devp.agent.training.TrainingRepository;
import com.devp.agent.training.TrainingService;
import com.devp.agent.training.TrainingStatistics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Runs an experiment: the same agent on the same task under several profiles, each for N
 * runs, training after training.
 *
 * <p>The baseline training is added unless it is switched off. Without it the other trainings measure
 * nothing: a rising pass rate may just as well be model variance, and only a flat reference
 * curve under identical conditions tells the difference.
 *
 * <p>Trainings run strictly one after another on the single training thread. Running them in
 * parallel would mean several trainings hitting the same provider at once — the numbers
 * would then carry the provider's load as an unrecorded variable.
 */
@Service
public class ExperimentService {

	private static final Logger log = LoggerFactory.getLogger(ExperimentService.class);

	private final ExperimentRepository experiments;
	private final TrainingRepository trainings;
	private final TrainingService training;
	private final TaskRepository tasks;
	private final AgentRepository agents;
	private final ProfileRepository profiles;
	private final ProfileProperties profileProperties;
	private final ChatClientRegistry providers;
	private final AgentProperties properties;
	private final ExperimentService self;

	public ExperimentService(ExperimentRepository experiments, TrainingRepository trainings, TrainingService training,
			TaskRepository tasks, AgentRepository agents, ProfileRepository profiles,
			ProfileProperties profileProperties, ChatClientRegistry providers, AgentProperties properties,
			@Lazy ExperimentService self) {
		this.experiments = experiments;
		this.trainings = trainings;
		this.training = training;
		this.tasks = tasks;
		this.agents = agents;
		this.profiles = profiles;
		this.profileProperties = profileProperties;
		this.providers = providers;
		this.properties = properties;
		// @Async only works through the proxy — a plain this.execute() would run inline.
		this.self = self;
	}

	/**
	 * Creates the experiment with all its trainings and schedules them. Everything that must stay
	 * equal between the trainings is resolved once, here.
	 *
	 * @param profileIds      the profiles to compare; the baseline is added on top unless excluded
	 * @param includeBaseline add the default profile as a reference training
	 */
	public Experiment start(String name, String taskId, int repetitions, String agentId, String providerId,
			String soulId, List<String> profileIds, boolean includeBaseline) {
		return start(null, name, taskId, repetitions, agentId, providerId, soulId, profileIds, includeBaseline);
	}

	/** The same, as one execution of a saved plan. */
	public Experiment start(UUID planId, String name, String taskId, int repetitions, String agentId,
			String providerId, String soulId, List<String> profileIds, boolean includeBaseline) {

		Task task = tasks.resolve(taskId);
		Agent agent = agents.resolve(agentId);
		String provider = providers.resolve(orDefault(providerId, orDefault(agent.provider(), task.provider()))).id();
		List<String> trainingProfiles = trainingProfiles(profileIds, includeBaseline);

		if (repetitions < 1 || repetitions > properties.getMaxTrainingRepetitions()) {
			throw new IllegalArgumentException("repetitions must be between 1 and %d."
					.formatted(properties.getMaxTrainingRepetitions()));
		}
		if (trainingProfiles.size() > properties.getMaxExperimentTrainings()) {
			throw new IllegalArgumentException("An experiment compares at most %d profiles, asked for %d."
					.formatted(properties.getMaxExperimentTrainings(), trainingProfiles.size()));
		}

		Experiment created = new Experiment(UUID.randomUUID(), planId, name, task.id(), task.version(), agent.id(),
				agent.version(), provider, repetitions, ExperimentStatus.RUNNING, null, Instant.now(), null);
		experiments.create(created);

		List<Training> queued = new ArrayList<>();
		for (String profileId : trainingProfiles) {
			queued.add(training.create(created.id(), task.id(), repetitions, agent.id(), provider, soulId, profileId));
		}
		log.info("Experiment {} created: task {} v{}, agent {} v{}, {} training(s) x{}: {}", created.id(), task.id(),
				task.version(), agent.id(), agent.version(), trainingProfiles.size(), repetitions, trainingProfiles);

		self.execute(created, queued);
		return created;
	}

	/** Profiles to compare, baseline first, without duplicates and each one verified to exist. */
	private List<String> trainingProfiles(List<String> profileIds, boolean includeBaseline) {
		Set<String> ids = new LinkedHashSet<>();
		if (includeBaseline) {
			ids.add(profiles.resolve(profileProperties.getDefaultProfile()).id());
		}
		if (profileIds != null) {
			profileIds.stream()
					.filter(id -> id != null && !id.isBlank())
					.map(id -> profiles.resolve(id).id())
					.forEach(ids::add);
		}
		if (ids.isEmpty()) {
			throw new IllegalArgumentException("An experiment needs at least one profile.");
		}
		return List.copyOf(ids);
	}

	@Async("trainingExecutor")
	public void execute(Experiment created, List<Training> planned) {
		try {
			for (Training next : planned) {
				// Read before every training, not once: stopping happens while this loop runs.
				if (cancelled(created.id())) {
					log.info("Experiment {} cancelled", created.id());
					trainings.cancelPending(created.id());
					return;
				}
				training.runNow(next);
			}
			if (cancelled(created.id())) {
				trainings.cancelPending(created.id());
				return;
			}
			experiments.finish(created.finished(ExperimentStatus.DONE, null, Instant.now()));
			log.info("Experiment {} done: {} training(s)", created.id(), planned.size());
		}
		catch (RuntimeException ex) {
			log.error("Experiment {} aborted: {}", created.id(), ex.getMessage());
			experiments.finish(created.finished(ExperimentStatus.FAILED,
					"%s: %s".formatted(ex.getClass().getSimpleName(), ex.getMessage()), Instant.now()));
		}
	}

	private boolean cancelled(UUID id) {
		return experiments.find(id).filter(e -> e.status() == ExperimentStatus.CANCELLED).isPresent();
	}

	/**
	 * Stops an experiment: the trainings that have not started are cancelled at once, the training in
	 * progress after its current run. What already ran stays — it is data, not waste.
	 *
	 * @return false if the experiment had already finished
	 */
	public boolean cancel(UUID id) {
		find(id).orElseThrow(() -> new IllegalArgumentException("Unknown experiment '%s'.".formatted(id)));
		boolean stopped = experiments.cancel(id) > 0;
		trainings.cancelPending(id);
		return stopped;
	}

	/** Executions of one plan, newest first. */
	public List<Experiment> listForPlan(UUID planId) {
		return experiments.listForPlan(planId, properties.getHistoryLimit());
	}

	public Optional<Experiment> find(UUID id) {
		return experiments.find(id);
	}

	public List<Experiment> list(String taskId) {
		return taskId == null || taskId.isBlank()
				? experiments.list(properties.getHistoryLimit())
				: experiments.listForTask(taskId, properties.getHistoryLimit());
	}

	/** The trainings of an experiment with their statistics — one request instead of one per training. */
	public List<TrainingSummary> trainings(UUID experimentId) {
		return trainings.listForExperiment(experimentId).stream()
				.map(t -> new TrainingSummary(t, training.statistics(t, training.runsOf(t.id())), training.memory(t.id()).size()))
				.toList();
	}

	private static String orDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	/**
	 * One training as the comparison shows it: the training, its statistics and how much its
	 * strategies remembered. The memory itself stays behind the training endpoint — a
	 * comparison table must not carry every lesson of every training.
	 */
	public record TrainingSummary(Training training, TrainingStatistics statistics, int memoryEntries) {
	}
}
