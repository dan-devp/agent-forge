package com.devp.agent.series;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Runs a task N times and keeps the results together.
 *
 * <p>The series is created synchronously and returned at once; the runs happen on the
 * {@code seriesExecutor}, one at a time. Sequential on purpose: the budget, the mock logs
 * and the trace stay readable, and a local model is not asked to serve ten requests in
 * parallel. Progress is polled via {@code GET /api/series/{id}}.
 *
 * <p>A failed run does not fail the series — it is a data point with status FAILED. Only a
 * failure of the series itself (task vanished, unexpected error in the loop) ends it as FAILED.
 */
@Service
public class SeriesService {

	private static final Logger log = LoggerFactory.getLogger(SeriesService.class);

	private final SeriesRepository series;
	private final RunRepository runs;
	private final AgentService agent;
	private final TaskRepository tasks;
	private final SoulRepository souls;
	private final ProfileRepository profiles;
	private final ChatClientRegistry providers;
	private final AgentProperties properties;
	private final SeriesService self;

	public SeriesService(SeriesRepository series, RunRepository runs, AgentService agent, TaskRepository tasks,
			SoulRepository souls, ProfileRepository profiles, ChatClientRegistry providers,
			AgentProperties properties, @org.springframework.context.annotation.Lazy SeriesService self) {
		this.series = series;
		this.runs = runs;
		this.agent = agent;
		this.tasks = tasks;
		this.souls = souls;
		this.profiles = profiles;
		this.providers = providers;
		this.properties = properties;
		// @Async only works through the proxy — a plain this.execute() would run inline.
		this.self = self;
	}

	/**
	 * Creates the series and schedules its runs. Resolves task, SOUL, profile and provider
	 * once, so all runs share the same settings even if a file is reloaded meanwhile.
	 */
	public Series start(String taskId, int repetitions, String providerId, String soulId, String profileId) {
		Task task = tasks.resolve(taskId);
		if (repetitions < 1 || repetitions > properties.getMaxSeriesRepetitions()) {
			throw new IllegalArgumentException("repetitions must be between 1 and %d."
					.formatted(properties.getMaxSeriesRepetitions()));
		}
		String soul = souls.resolve(orDefault(soulId, task.soul())).id();
		String profile = profiles.resolve(orDefault(profileId, task.profile())).id();
		String provider = providers.resolve(orDefault(providerId, task.provider())).id();

		Series created = new Series(UUID.randomUUID(), task.id(), task.version(), soul, profile, provider,
				repetitions, SeriesStatus.RUNNING, 0, null, Instant.now(), null);
		series.create(created);
		log.info("Series {} created: task {} v{} x{}, SOUL {}, profile {}, provider {}", created.id(), task.id(),
				task.version(), repetitions, soul, profile, provider);

		self.execute(created);
		return created;
	}

	@Async("seriesExecutor")
	public void execute(Series created) {
		int completed = 0;
		try {
			for (int i = 0; i < created.repetitions(); i++) {
				try {
					agent.run(null, created.taskId(), created.providerId(), created.soulId(), created.profileId(),
							created.id());
				}
				catch (RuntimeException ex) {
					// The run itself is already recorded as FAILED; the series continues.
					log.warn("Series {}: run {} of {} failed: {}", created.id(), i + 1, created.repetitions(),
							ex.getMessage());
				}
				completed++;
				series.progress(created.id(), completed);
			}
			series.finish(created.withProgress(completed).finished(SeriesStatus.DONE, null, Instant.now()));
			log.info("Series {} done: {} run(s)", created.id(), completed);
		}
		catch (RuntimeException ex) {
			log.error("Series {} aborted after {} run(s): {}", created.id(), completed, ex.getMessage());
			series.finish(created.withProgress(completed).finished(SeriesStatus.FAILED,
					"%s: %s".formatted(ex.getClass().getSimpleName(), ex.getMessage()), Instant.now()));
		}
	}

	public Optional<Series> find(UUID id) {
		return series.find(id);
	}

	public List<Series> list() {
		return series.list(properties.getHistoryLimit());
	}

	/** The runs of a series with scores but without traces, in execution order. */
	public List<AgentRun> runsOf(UUID seriesId) {
		return series.runIds(seriesId).stream()
				.map(runs::find)
				.flatMap(Optional::stream)
				.map(run -> new AgentRun(run.id(), run.goal(), run.taskId(), run.taskVersion(), run.soulId(),
						run.soulVersion(), run.profileId(), run.profileVersion(), run.seriesId(), run.providerId(),
						run.model(), run.temperature(), run.systemPrompt(), run.tools(), run.status(), run.answer(),
						run.error(), run.steps(), run.totalTokens(), run.durationMs(), run.startedAt(),
						run.finishedAt(), List.of(), run.scores()))
				.toList();
	}

	public SeriesStatistics statistics(Series s, List<AgentRun> seriesRuns) {
		return SeriesStatistics.of(s, seriesRuns, series.toolCallsByRun(s.id()));
	}

	private static String orDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}
}
