package com.devp.agent.run;

import java.time.Instant;
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
import com.devp.agent.run.TracingToolCallingManager.BudgetExceededException;
import com.devp.agent.soul.Soul;
import com.devp.agent.soul.SoulRenderer;
import com.devp.agent.soul.SoulRepository;
import com.devp.agent.task.Task;
import com.devp.agent.task.TaskRepository;
import com.devp.agent.tools.ToolRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.stereotype.Service;

/**
 * Executes a run: goal in, a loop of model rounds and tool calls, answer out — and a
 * complete trace in the database.
 *
 * <p>The loop is driven by Spring AI's {@code ToolCallingAdvisor}: as long as the model
 * requests tools, it has them executed through the {@link TracingToolCallingManager} and
 * starts the next round. This service sets the frame — identity, tools, budget, recording.
 */
@Service
public class AgentService {

	private static final Logger log = LoggerFactory.getLogger(AgentService.class);

	private final ChatClientRegistry providers;
	private final SoulRepository souls;
	private final TaskRepository tasks;
	private final SoulRenderer renderer;
	private final ToolRegistry tools;
	private final RunRepository runs;
	private final EvaluationService evaluation;
	private final AgentProperties properties;

	public AgentService(ChatClientRegistry providers, SoulRepository souls, TaskRepository tasks,
			SoulRenderer renderer, ToolRegistry tools, RunRepository runs, EvaluationService evaluation,
			AgentProperties properties) {
		this.providers = providers;
		this.souls = souls;
		this.tasks = tasks;
		this.renderer = renderer;
		this.tools = tools;
		this.runs = runs;
		this.evaluation = evaluation;
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
	 */
	public AgentRun run(String goal, String taskId, String providerId, String soulId) {
		Task task = (taskId == null || taskId.isBlank()) ? null : tasks.resolve(taskId);

		if (task == null && (goal == null || goal.isBlank())) {
			throw new IllegalArgumentException("Either 'task' or 'goal' is required.");
		}
		if (task != null) {
			goal = task.goal();
			soulId = orDefault(soulId, task.soul());
			providerId = orDefault(providerId, task.provider());
		}

		ResolvedProvider provider = providers.resolve(providerId);
		Soul soul = souls.resolve(soulId);

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

		AgentRun started = new AgentRun(id, goal, task == null ? null : task.id(),
				task == null ? null : task.version(), soul.id(), soul.version(), provider.id(),
				provider.config().getModel(), RunStatus.RUNNING, null, null, 0, null, null, start, null, List.of());
		runs.start(started);
		log.info("Run {} started: task {}, SOUL {} v{}, provider {}, {} tool(s)",
				id, task == null ? "(free-form)" : task.id() + " v" + task.version(), soul.id(), soul.version(),
				provider.id(), tools.callbacks().size());

		try {
			ChatResponse response = provider.client().prompt()
					.system(renderer.render(soul))
					.user(goal)
					.tools(tools.callbacks())
					.toolContext(toolContext(id, deadline, task))
					.call()
					.chatResponse();

			return complete(started, response, start, task);
		}
		catch (ToolCallLimitExceededException | BudgetExceededException ex) {
			// Budget is a result, not an error: the trace up to here stays usable.
			log.info("Run {} ended by budget: {}", id, ex.getMessage());
			return finish(started, RunStatus.BUDGET_EXCEEDED, null, ex.getMessage(), start, null, null, task);
		}
		catch (RuntimeException ex) {
			log.warn("Run {} failed: {}", id, ex.getMessage());
			finish(started, RunStatus.FAILED, null,
					"%s: %s".formatted(ex.getClass().getSimpleName(), ex.getMessage()), start, null, null, task);
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

	/** What the loop body needs to know about this run. */
	private static Map<String, Object> toolContext(UUID id, long deadline, Task task) {
		Map<String, Object> context = new LinkedHashMap<>();
		context.put(TracingToolCallingManager.RUN_ID, id);
		context.put(TracingToolCallingManager.DEADLINE, deadline);
		if (task != null && task.maxToolCalls() != null) {
			context.put(TracingToolCallingManager.MAX_TOOL_CALLS, task.maxToolCalls());
		}
		return context;
	}

	private static String orDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private AgentRun complete(AgentRun started, ChatResponse response, Instant start, Task task) {
		if (response == null || response.getResult() == null) {
			return finish(started, RunStatus.FAILED, null, "Provider returned an empty response.", start, null,
					null, task);
		}

		Integer tokens = response.getMetadata().getUsage() == null
				? null
				: response.getMetadata().getUsage().getTotalTokens();
		String text = response.getResult().getOutput().getText();

		// Final round: the only model round that requests no tools and therefore does not
		// pass through the ToolCallingManager.
		runs.addStep(started.id(), new RunStep(
				runs.nextStepNumber(started.id()), StepType.MODEL, response.getMetadata().getModel(),
				null, text, tokens, null, null, Instant.now()));

		String model = response.getMetadata().getModel();
		return finish(started, RunStatus.DONE, text, null, start,
				model == null || model.isBlank() ? started.model() : model, tokens, task);
	}

	/**
	 * Writes the final state and then lets the graders run. Every final state is graded,
	 * including budget and failure — a run without an answer fails its criteria, and exactly
	 * that belongs in the statistics instead of being a gap.
	 */
	private AgentRun finish(AgentRun started, RunStatus status, String answer, String error, Instant start,
			String model, Integer tokens, Task task) {

		List<RunStep> trace = runs.trace(started.id());
		Instant end = Instant.now();
		Integer total = tokens != null ? tokens : trace.stream()
				.map(RunStep::tokens)
				.filter(Objects::nonNull)
				.reduce(Integer::sum)
				.orElse(null);

		AgentRun finished = new AgentRun(
				started.id(), started.goal(), started.taskId(), started.taskVersion(), started.soulId(),
				started.soulVersion(), started.providerId(), model == null ? started.model() : model,
				status, answer, error, trace.size(), total, end.toEpochMilli() - start.toEpochMilli(), start,
				end, trace);

		runs.finish(finished);
		return finished.withScores(evaluation.evaluate(finished, task));
	}

	public Optional<AgentRun> find(UUID id) {
		return runs.find(id);
	}

	public List<AgentRun> history() {
		return runs.list(properties.getHistoryLimit());
	}
}
