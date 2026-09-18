package com.devp.agent.run;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.devp.agent.ai.ChatClientRegistry;
import com.devp.agent.ai.ChatClientRegistry.ResolvedProvider;
import com.devp.agent.ai.ChatService.AiNotConfiguredException;
import com.devp.agent.ai.ChatService.ModelCallFailedException;
import com.devp.agent.config.AgentProperties;
import com.devp.agent.eval.EvaluationService;
import com.devp.agent.profile.Profile;
import com.devp.agent.profile.ProfileRepository;
import com.devp.agent.run.TracingToolCallingManager.BudgetExceededException;
import com.devp.agent.soul.Soul;
import com.devp.agent.soul.SoulRenderer;
import com.devp.agent.soul.SoulRepository;
import com.devp.agent.strategy.MemoryStore;
import com.devp.agent.strategy.RunContext;
import com.devp.agent.strategy.StrategyRegistry;
import com.devp.agent.strategy.StrategyRegistry.Active;
import com.devp.agent.task.Task;
import com.devp.agent.task.TaskRepository;
import com.devp.agent.tools.ToolRegistry;
import com.devp.agent.training.TrainingMemoryStore;
import com.devp.agent.training.TrainingRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

/**
 * Executes a run: goal in, a loop of model rounds and tool calls, answer out — and a
 * complete trace in the database.
 *
 * <p>The tool loop is driven by Spring AI's {@code ToolCallingAdvisor}: as long as the model
 * requests tools, it has them executed through the {@link TracingToolCallingManager} and
 * starts the next round. This service sets the frame — identity, tools, budget, recording —
 * and calls the strategy hooks of the active profile at the five points of the loop.
 */
@Service
public class AgentService {

	private static final Logger log = LoggerFactory.getLogger(AgentService.class);

	private final ChatClientRegistry providers;
	private final SoulRepository souls;
	private final TaskRepository tasks;
	private final ProfileRepository profiles;
	private final StrategyRegistry strategies;
	private final SoulRenderer renderer;
	private final ToolRegistry tools;
	private final RunRepository runs;
	private final EvaluationService evaluation;
	private final TrainingRepository trainings;
	private final AgentProperties properties;

	public AgentService(ChatClientRegistry providers, SoulRepository souls, TaskRepository tasks,
			ProfileRepository profiles, StrategyRegistry strategies, SoulRenderer renderer, ToolRegistry tools,
			RunRepository runs, EvaluationService evaluation, TrainingRepository trainings, AgentProperties properties) {
		this.providers = providers;
		this.souls = souls;
		this.tasks = tasks;
		this.profiles = profiles;
		this.strategies = strategies;
		this.renderer = renderer;
		this.tools = tools;
		this.runs = runs;
		this.evaluation = evaluation;
		this.trainings = trainings;
		this.properties = properties;
	}

	/**
	 * Starts a run and waits for it to finish.
	 *
	 * <p>Either {@code taskId} or {@code goal} — a named task is the path that can be
	 * evaluated later, a free-form goal the path for trying things out. Values given on the
	 * call take precedence over the task's defaults.
	 *
	 * @param goal       free-form goal, {@code null} when a task is named
	 * @param taskId     task id or {@code null}
	 * @param providerId provider id, or {@code null} for the task default resp. the global default
	 * @param soulId     SOUL id, or {@code null} for the task default resp. the active SOUL
	 * @param profileId  profile id, or {@code null} for the task default resp. {@code baseline}
	 * @param trainingId the training this run belongs to, or {@code null} for a single run
	 */
	public AgentRun run(String goal, String taskId, String providerId, String soulId, String profileId,
			UUID trainingId) {
		Task task = (taskId == null || taskId.isBlank()) ? null : tasks.resolve(taskId);

		if (task == null && (goal == null || goal.isBlank())) {
			throw new IllegalArgumentException("Either 'task' or 'goal' is required.");
		}
		if (task != null) {
			goal = task.goal();
			soulId = orDefault(soulId, task.soul());
			providerId = orDefault(providerId, task.provider());
			profileId = orDefault(profileId, task.profile());
		}

		ResolvedProvider provider = providers.resolve(providerId);
		Soul soul = souls.resolve(soulId);
		Profile profile = profiles.resolve(profileId);
		List<Active> active = strategies.activate(profile);

		if (!provider.config().isConfigured()) {
			throw new AiNotConfiguredException(
					"Provider '%s' is not fully configured (check base URL, model and API key)."
							.formatted(provider.id()));
		}

		UUID id = UUID.randomUUID();
		Instant start = Instant.now();
		long timeBudget = task != null && task.maxDurationMs() != null
				? task.maxDurationMs()
				: properties.getMaxDurationMs();
		long deadline = System.currentTimeMillis() + timeBudget;

		// Hook 1: strategies may extend the prompt and offer tools before the first round. Inside a
		// training they also see what earlier runs remembered; alone they get an empty store.
		MemoryStore memory = trainingId == null ? MemoryStore.NONE : new TrainingMemoryStore(trainings, trainingId, id);
		RunContext context = new RunContext(id, goal, task, soul, profile, memory);
		active.forEach(a -> a.strategy().beforeRun(context, a.config()));

		// Record what the model will see before the first call — if the run crashes, the
		// parameters are still there.
		String systemPrompt = systemPrompt(soul, context);
		List<ToolCallback> offered = new ArrayList<>(tools.callbacks());
		offered.addAll(context.extraTools());
		List<String> offeredNames = offered.stream().map(t -> t.getToolDefinition().name()).toList();

		AgentRun started = new AgentRun(id, goal, task == null ? null : task.id(),
				task == null ? null : task.version(), soul.id(), soul.version(), profile.id(), profile.version(),
				trainingId, provider.id(), provider.config().getModel(), provider.config().getTemperature(), systemPrompt,
				offeredNames, RunStatus.RUNNING, null, null, 0, null, null, start, null, List.of(), List.of());
		runs.start(started);
		log.info("Run {} started: task {}, SOUL {} v{}, profile {} v{} ({} strateg{}), provider {}, {} tool(s)",
				id, task == null ? "(free-form)" : task.id() + " v" + task.version(), soul.id(), soul.version(),
				profile.id(), profile.version(), active.size(), active.size() == 1 ? "y" : "ies", provider.id(),
				offered.size());

		Map<String, Object> toolContext = toolContext(id, deadline, task, context, active);
		List<Message> conversation = new ArrayList<>(List.of(new SystemMessage(systemPrompt), new UserMessage(goal)));
		Map<String, Integer> rejections = new HashMap<>();

		try {
			while (true) {
				ChatResponse response = provider.client().prompt()
						.messages(conversation)
						.tools(offered)
						.toolContext(toolContext)
						.call()
						.chatResponse();

				if (response == null || response.getResult() == null) {
					return finish(started, RunStatus.FAILED, null, "Provider returned an empty response.", start,
							null, null, task, context, active);
				}

				String answer = response.getResult().getOutput().getText();
				Integer tokens = response.getMetadata().getUsage() == null
						? null
						: response.getMetadata().getUsage().getTotalTokens();
				String model = response.getMetadata().getModel();

				// Final round of this attempt: the only model round that requests no tools and
				// therefore does not pass through the ToolCallingManager.
				runs.addStep(id, new RunStep(runs.nextStepNumber(id), StepType.MODEL, model, null, answer, tokens,
						null, null, Instant.now()));

				// Hook 3: a strategy may reject the answer and send the model back.
				Optional<Rejection> rejection = verify(context, active, answer, rejections);
				if (rejection.isEmpty()) {
					return finish(started, RunStatus.DONE, answer, null, start,
							model == null || model.isBlank() ? started.model() : model, tokens, task, context, active);
				}

				Rejection r = rejection.get();
				runs.addStep(id, new RunStep(runs.nextStepNumber(id), StepType.VERIFY, r.strategyId(), answer,
						r.feedback(), null, null, null, Instant.now()));
				if (r.exhausted()) {
					log.info("Run {} rejected by '{}' in every allowed round", id, r.strategyId());
					return finish(started, RunStatus.VERIFICATION_FAILED, answer,
							"Strategy '%s' rejected the answer %d time(s): %s".formatted(r.strategyId(),
									rejections.get(r.strategyId()), r.feedback()),
							start, model, tokens, task, context, active);
				}
				log.info("Run {} rejected by '{}', sending feedback (round {})", id, r.strategyId(),
						rejections.get(r.strategyId()));
				// Only the final text of the attempt is carried over, not its tool turns — the
				// advisor keeps those within one call, and the trace has them anyway.
				conversation.add(new AssistantMessage(answer == null ? "" : answer));
				conversation.add(new UserMessage(r.feedback()));
			}
		}
		catch (ToolCallLimitExceededException | BudgetExceededException ex) {
			// Budget is a result, not an error: the trace up to here stays usable.
			log.info("Run {} ended by budget: {}", id, ex.getMessage());
			return finish(started, RunStatus.BUDGET_EXCEEDED, null, ex.getMessage(), start, null, null, task,
					context, active);
		}
		catch (RuntimeException ex) {
			log.warn("Run {} failed: {}", id, ex.getMessage());
			finish(started, RunStatus.FAILED, null,
					"%s: %s".formatted(ex.getClass().getSimpleName(), ex.getMessage()), start, null, null, task,
					context, active);
			if (ex instanceof AiNotConfiguredException || ex instanceof ModelCallFailedException) {
				throw ex;
			}
			throw new ModelCallFailedException(
					"Run %s against provider '%s' (%s) failed: %s"
							.formatted(id, provider.id(), provider.config().getBaseUrl(), ex.getMessage()),
					ex);
		}
		finally {
			log.debug("Run {} finished", id);
		}
	}

	/** A single run outside any training. */
	public AgentRun run(String goal, String taskId, String providerId, String soulId, String profileId) {
		return run(goal, taskId, providerId, soulId, profileId, null);
	}

	/** Compatibility for callers without a profile. */
	public AgentRun run(String goal, String taskId, String providerId, String soulId) {
		return run(goal, taskId, providerId, soulId, null, null);
	}

	/** The rendered SOUL plus whatever the strategies added, separated so both stay recognizable. */
	private String systemPrompt(Soul soul, RunContext context) {
		StringBuilder prompt = new StringBuilder(renderer.render(soul));
		for (String addition : context.promptAdditions()) {
			prompt.append("\n\n").append(addition);
		}
		return prompt.toString();
	}

	/**
	 * Asks every active strategy in profile order; the first rejection wins. A strategy that
	 * has used up its {@code max-rounds} turns the rejection into a final failure — and so
	 * does the global cap, so a misconfigured profile cannot loop until the time budget hits.
	 */
	private Optional<Rejection> verify(RunContext context, List<Active> active, String answer,
			Map<String, Integer> rejections) {
		for (Active a : active) {
			Optional<String> feedback = a.strategy().beforeFinish(context, a.config(), answer);
			if (feedback.isEmpty()) {
				continue;
			}
			int count = rejections.merge(a.strategy().id(), 1, Integer::sum);
			int allowed = Math.min(a.config().intParam("max-rounds", 1), properties.getMaxVerifyRounds());
			return Optional.of(new Rejection(a.strategy().id(), feedback.get(), count > allowed));
		}
		return Optional.empty();
	}

	private record Rejection(String strategyId, String feedback, boolean exhausted) {
	}

	/** What the loop body needs to know about this run. */
	private static Map<String, Object> toolContext(UUID id, long deadline, Task task, RunContext context,
			List<Active> active) {
		Map<String, Object> toolContext = new LinkedHashMap<>();
		toolContext.put(TracingToolCallingManager.RUN_ID, id);
		toolContext.put(TracingToolCallingManager.DEADLINE, deadline);
		toolContext.put(TracingToolCallingManager.RUN_CONTEXT, context);
		toolContext.put(TracingToolCallingManager.STRATEGIES, active);
		if (task != null && task.maxToolCalls() != null) {
			toolContext.put(TracingToolCallingManager.MAX_TOOL_CALLS, task.maxToolCalls());
		}
		return toolContext;
	}

	private static String orDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	/**
	 * Writes the final state, lets the graders run and then gives every strategy the graded
	 * run (hook 4). Every final state is graded, including budget and failure — a run without
	 * an answer fails its criteria, and exactly that belongs in the statistics.
	 */
	private AgentRun finish(AgentRun started, RunStatus status, String answer, String error, Instant start,
			String model, Integer tokens, Task task, RunContext context, List<Active> active) {

		List<RunStep> trace = runs.trace(started.id());
		Instant end = Instant.now();
		Integer total = tokens != null ? tokens : trace.stream()
				.map(RunStep::tokens)
				.filter(Objects::nonNull)
				.reduce(Integer::sum)
				.orElse(null);

		AgentRun finished = new AgentRun(
				started.id(), started.goal(), started.taskId(), started.taskVersion(), started.soulId(),
				started.soulVersion(), started.profileId(), started.profileVersion(), started.trainingId(),
				started.providerId(),
				model == null ? started.model() : model, started.temperature(), started.systemPrompt(),
				started.tools(), status, answer, error, trace.size(), total,
				end.toEpochMilli() - start.toEpochMilli(), start, end, trace, List.of());

		runs.finish(finished);
		AgentRun graded = finished.withScores(evaluation.evaluate(finished, task));

		for (Active a : active) {
			try {
				a.strategy().afterRun(context, a.config(), graded, graded.scores());
			}
			catch (RuntimeException ex) {
				// The run is history; a strategy that fails to learn from it must not undo that.
				log.warn("Strategy '{}' failed in afterRun of run {}: {}", a.strategy().id(), graded.id(),
						ex.getMessage());
			}
		}
		return graded;
	}

	public Optional<AgentRun> find(UUID id) {
		return runs.find(id);
	}

	public List<AgentRun> history() {
		return runs.list(properties.getHistoryLimit());
	}
}
