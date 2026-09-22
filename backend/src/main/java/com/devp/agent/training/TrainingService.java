package com.devp.agent.training;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.devp.agent.agent.Agent;
import com.devp.agent.agent.AgentRepository;
import com.devp.agent.ai.ChatClientRegistry;
import com.devp.agent.config.AgentProperties;
import com.devp.agent.profile.ProfileRepository;
import com.devp.agent.run.AgentRun;
import com.devp.agent.run.AgentService;
import com.devp.agent.run.RunRepository;
import com.devp.agent.soul.SoulRepository;
import com.devp.agent.task.Task;
import com.devp.agent.task.TaskRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Trains one profile on one task: N runs in order, each able to build on what the
 * strategies remembered from the runs before.
 *
 * <p>The training is created synchronously and returned at once; the runs happen on the
 * {@code trainingExecutor}, one at a time. Sequential is not a convenience here but the
 * point: run k must see the memory of runs 1..k-1. Progress is polled via
 * {@code GET /api/trainings/{id}}.
 *
 * <p>A failed run does not fail the training — it is a data point with status FAILED. Only a
 * failure of the training itself (task vanished, unexpected error in the loop) ends it as FAILED.
 */
@Service
public class TrainingService {

	private static final Logger log = LoggerFactory.getLogger(TrainingService.class);

	private final TrainingRepository trainings;
	private final RunRepository runs;
	private final AgentService runner;
	private final AgentRepository agents;
	private final TaskRepository tasks;
	private final SoulRepository souls;
	private final ProfileRepository profiles;
	private final ChatClientRegistry providers;
	private final AgentProperties properties;
	private final TrainingService self;

	public TrainingService(TrainingRepository trainings, RunRepository runs, AgentService runner,
			AgentRepository agents, TaskRepository tasks, SoulRepository souls, ProfileRepository profiles,
			ChatClientRegistry providers, AgentProperties properties, @Lazy TrainingService self) {
		this.trainings = trainings;
		this.runs = runs;
		this.runner = runner;
		this.agents = agents;
		this.tasks = tasks;
		this.souls = souls;
		this.profiles = profiles;
		this.providers = providers;
		this.properties = properties;
		// @Async only works through the proxy — a plain this.execute() would run inline.
		this.self = self;
	}

	/**
	 * Creates the training and schedules its runs. Resolves task, agent, SOUL, profile and
	 * provider once, so all runs share the same settings even if a file is reloaded meanwhile.
	 */
	public Training start(String taskId, int repetitions, String agentId, String providerId, String soulId,
			String profileId) {
		Training created = create(null, taskId, repetitions, agentId, providerId, soulId, profileId);
		self.execute(created);
		return created;
	}

	/**
	 * Creates a training without starting it — the experiment schedules its trainings itself, so
	 * that they run one after another instead of fighting over the single training thread.
	 */
	public Training create(UUID experimentId, String taskId, int repetitions, String agentId, String providerId,
			String soulId, String profileId) {
		Task task = tasks.resolve(taskId);
		if (repetitions < 1 || repetitions > properties.getMaxTrainingRepetitions()) {
			throw new IllegalArgumentException("repetitions must be between 1 and %d."
					.formatted(properties.getMaxTrainingRepetitions()));
		}
		Agent agent = agents.resolve(agentId);
		String soul = souls.resolve(orDefault(soulId, orDefault(agent.soul(), task.soul()))).id();
		String profile = profiles.resolve(orDefault(profileId, task.profile())).id();
		String provider = providers.resolve(orDefault(providerId, orDefault(agent.provider(), task.provider()))).id();

		// A training waits for the trainings before it; a training started on its own begins at once.
		TrainingStatus status = experimentId == null ? TrainingStatus.RUNNING : TrainingStatus.QUEUED;
		Training created = new Training(UUID.randomUUID(), experimentId, task.id(), task.version(), agent.id(),
				agent.version(), soul, profile, provider, repetitions, status, 0, null, Instant.now(), null);
		trainings.create(created);
		log.info("Training {} created: task {} v{} x{}, agent {} v{}, SOUL {}, profile {}, provider {}", created.id(),
				task.id(), task.version(), repetitions, agent.id(), agent.version(), soul, profile, provider);
		return created;
	}

	@Async("trainingExecutor")
	public void execute(Training created) {
		runNow(created);
	}

	/**
	 * The loop itself, synchronous. Called by {@link #execute} on the training thread and
	 * directly by the experiment, which is already on that thread.
	 */
	public void runNow(Training created) {
		int completed = 0;
		if (trainings.markRunning(created.id()) == 0 && cancelled(created.id())) {
			// Cancelled while it was still waiting in the queue. Nothing to do, nothing to write.
			log.info("Training {} was cancelled before it started", created.id());
			return;
		}
		try {
			for (int i = 0; i < created.repetitions(); i++) {
				// Asked before every run, not during: a model call in flight is not interrupted,
				// so the answer it was paid for still becomes a data point.
				if (cancelled(created.id())) {
					log.info("Training {} cancelled after {} of {} run(s)", created.id(), completed,
							created.repetitions());
					trainings.finish(created.withProgress(completed)
							.finished(TrainingStatus.CANCELLED, null, Instant.now()));
					return;
				}
				try {
					runner.run(null, created.taskId(), created.agentId(), created.providerId(), created.soulId(),
							created.profileId(), created.id());
				}
				catch (RuntimeException ex) {
					// The run itself is already recorded as FAILED; the training continues.
					log.warn("Training {}: run {} of {} failed: {}", created.id(), i + 1, created.repetitions(),
							ex.getMessage());
				}
				completed++;
				trainings.progress(created.id(), completed);
			}
			trainings.finish(created.withProgress(completed).finished(TrainingStatus.DONE, null, Instant.now()));
			log.info("Training {} done: {} run(s)", created.id(), completed);
		}
		catch (RuntimeException ex) {
			log.error("Training {} aborted after {} run(s): {}", created.id(), completed, ex.getMessage());
			trainings.finish(created.withProgress(completed).finished(TrainingStatus.FAILED,
					"%s: %s".formatted(ex.getClass().getSimpleName(), ex.getMessage()), Instant.now()));
		}
	}

	/** The cancel flag lives in the database, so it also reaches a loop on another thread. */
	private boolean cancelled(UUID id) {
		return trainings.status(id).filter(s -> s == TrainingStatus.CANCELLED).isPresent();
	}

	/**
	 * Stops a training. It takes effect before the next run — the current model call is paid
	 * for either way, so its answer is kept rather than thrown away.
	 *
	 * @return false if the training had already finished
	 */
	public boolean cancel(UUID id) {
		find(id).orElseThrow(() -> new IllegalArgumentException("Unknown training '%s'.".formatted(id)));
		return trainings.cancel(id) > 0;
	}

	public Optional<Training> find(UUID id) {
		return trainings.find(id);
	}

	public List<Training> list(String taskId) {
		return taskId == null || taskId.isBlank()
				? trainings.list(properties.getHistoryLimit())
				: trainings.listForTask(taskId, properties.getHistoryLimit());
	}

	/** The runs of a training with scores but without traces, in execution order. */
	public List<AgentRun> runsOf(UUID trainingId) {
		return trainings.runIds(trainingId).stream()
				.map(runs::find)
				.flatMap(Optional::stream)
				.map(AgentRun::withoutTrace)
				.toList();
	}

	public TrainingStatistics statistics(Training training, List<AgentRun> trainingRuns) {
		return TrainingStatistics.of(training, trainingRuns, trainings.toolCallsByRun(training.id()));
	}

	public List<TrainingMemory> memory(UUID trainingId) {
		return trainings.memory(trainingId);
	}

	private static String orDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}
}
