package com.devp.forge.run;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.devp.forge.agent.Agent;
import com.devp.forge.agent.AgentRepository;
import com.devp.forge.ai.ChatClientRegistry;
import com.devp.forge.ai.ChatClientRegistry.AiNotConfiguredException;
import com.devp.forge.ai.ChatClientRegistry.ModelCallFailedException;
import com.devp.forge.config.RunProperties;
import com.devp.forge.eval.EvaluationService;
import com.devp.forge.model.Model;
import com.devp.forge.model.ModelKind;
import com.devp.forge.model.ModelRepository;
import com.devp.forge.run.TracingToolCallingManager.BudgetExceededException;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;
import com.devp.forge.task.Task;
import com.devp.forge.task.TaskRepository;
import com.devp.forge.tools.ToolRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

/**
 * Executes a run: agent and task in, a loop of model rounds and tool calls, answer out,
 * verdicts appended — and a complete trace in the database.
 *
 * <p>Two steps, so a batch can create many runs first and execute them later:
 * {@link #prepare} resolves everything and records what the model will see;
 * {@link #execute} runs a prepared run, re-resolving agent, model and task by the recorded
 * versions — never by "current", which may have moved on while the run waited.
 */
@Service
public class RunService {

	private static final Logger log = LoggerFactory.getLogger(RunService.class);

	private final ChatClientRegistry clients;
	private final AgentRepository agents;
	private final ModelRepository models;
	private final TaskRepository tasks;
	private final ToolRegistry tools;
	private final RunRepository runs;
	private final EvaluationService evaluation;
	private final RunProperties properties;

	public RunService(ChatClientRegistry clients, AgentRepository agents, ModelRepository models,
			TaskRepository tasks, ToolRegistry tools, RunRepository runs, EvaluationService evaluation,
			RunProperties properties) {
		this.clients = clients;
		this.agents = agents;
		this.models = models;
		this.tasks = tasks;
		this.tools = tools;
		this.runs = runs;
		this.evaluation = evaluation;
		this.properties = properties;
	}

	/**
	 * One run, now, synchronously. Either a task or a free input; only a task run is
	 * evaluated.
	 *
	 * @param agentVersion explicit version, or {@code null} for the current one
	 */
	public Run run(String agentId, Integer agentVersion, String taskId, String input) {
		Agent agent = resolveAgent(agentId, agentVersion);
		Task task = taskId == null || taskId.isBlank() ? null : tasks.get(taskId);
		if (task == null && (input == null || input.isBlank())) {
			throw new IllegalArgumentException("Either 'task' or 'input' is required.");
		}
		Run prepared = prepare(agent, task, input, null, Instant.now());
		runs.create(prepared);
		return execute(prepared);
	}

	public Agent resolveAgent(String agentId, Integer agentVersion) {
		if (agentId == null || agentId.isBlank()) {
			throw new IllegalArgumentException("'agent' is required.");
		}
		return agentVersion == null ? agents.get(agentId) : agents.get(agentId, agentVersion);
	}

	/**
	 * Resolves model and tools and builds the run record with everything the model will see.
	 * Nothing is written here; {@code startedAt == null} means queued.
	 */
	public Run prepare(Agent agent, Task task, String input, UUID batchId, Instant startedAt) {
		Model model = models.get(agent.modelId());
		// A hand-edited agent file may still point at one; the save path already refuses it.
		if (model.kind() != ModelKind.CHAT) {
			throw new EntityInvalidException("model '%s' is a decision model and cannot drive agent '%s'"
					.formatted(model.id(), agent.id()));
		}
		if (!clients.isConfigured(model)) {
			throw new AiNotConfiguredException("Model '%s' is not usable: %s".formatted(model.id(), clients.problem(model)));
		}
		String userMessage = task != null ? task.input() : input;
		List<String> offered = tools.callbacks().stream()
				.map(t -> t.getToolDefinition().name())
				.filter(agent.tools()::allows)
				.toList();
		return new Run(UUID.randomUUID(), batchId, task == null ? null : task.id(),
				task == null ? null : task.version(), agent.id(), agent.version(), model.id(), model.version(),
				model.modelId(), model.temperature(), agent.systemPrompt(), userMessage, offered,
				startedAt == null ? RunStatus.QUEUED : RunStatus.RUNNING, null, null, 0, null, null, null,
				Instant.now(), startedAt, null, List.of(), List.of());
	}

	/**
	 * Runs a prepared, already persisted run whose status is RUNNING. Ends in a final status
	 * in every case; a failed call is recorded as FAILED and then rethrown for the caller.
	 */
	public Run execute(Run started) {
		UUID id = started.id();
		Instant start = started.startedAt() == null ? Instant.now() : started.startedAt();
		Task task = started.taskId() == null ? null : tasks.get(started.taskId(), started.taskVersion());
		Agent agent;
		Model model;
		try {
			agent = agents.get(started.agentId(), started.agentVersion());
			model = models.get(started.modelId(), started.modelVersion());
		}
		catch (RuntimeException ex) {
			// Archived between queueing and execution — the recipe is gone, nothing to run.
			return finish(started, RunStatus.FAILED, null, ex.getMessage(), start, null, null, task);
		}

		long deadline = System.currentTimeMillis() + properties.getMaxDurationMs();
		List<ToolCallback> offered = new ArrayList<>(tools.callbacks().stream()
				.filter(t -> started.tools().contains(t.getToolDefinition().name()))
				.toList());
		log.info("Run {} started: agent {} v{}, model {} v{}, task {}, {} tool(s)", id, agent.id(), agent.version(),
				model.id(), model.version(), task == null ? "(ad hoc)" : task.id() + " v" + task.version(),
				offered.size());

		Map<String, Object> toolContext = new LinkedHashMap<>();
		toolContext.put(TracingToolCallingManager.RUN_ID, id);
		toolContext.put(TracingToolCallingManager.DEADLINE, deadline);
		if (task != null && task.maxToolCalls() != null) {
			toolContext.put(TracingToolCallingManager.MAX_TOOL_CALLS, task.maxToolCalls());
		}

		try {
			ChatResponse response = clients.client(model).prompt()
					.system(started.systemPrompt())
					.user(started.input())
					.tools(offered)
					.toolContext(toolContext)
					.call()
					.chatResponse();

			if (response == null || response.getResult() == null) {
				return finish(started, RunStatus.FAILED, null, "Endpoint returned an empty response.", start, null,
						null, task);
			}

			String answer = response.getResult().getOutput().getText();
			Integer tokens = response.getMetadata().getUsage() == null
					? null
					: response.getMetadata().getUsage().getTotalTokens();
			String reported = response.getMetadata().getModel();

			// Final round: the only model round that requests no tools and therefore does not
			// pass through the ToolCallingManager.
			runs.addStep(id, new RunStep(runs.nextStepNumber(id), StepType.MODEL, reported, null, answer, tokens,
					null, null, Instant.now()));

			return finish(started, RunStatus.DONE, answer, null, start,
					reported == null || reported.isBlank() ? null : reported, tokens, task);
		}
		catch (ToolCallLimitExceededException | BudgetExceededException ex) {
			// Budget is a result, not an error: the trace up to here stays usable.
			log.info("Run {} ended by budget: {}", id, ex.getMessage());
			return finish(started, RunStatus.BUDGET_EXCEEDED, null, ex.getMessage(), start, null, null, task);
		}
		catch (RuntimeException ex) {
			log.warn("Run {} failed: {}", id, ex.getMessage());
			finish(started, RunStatus.FAILED, null, "%s: %s".formatted(ex.getClass().getSimpleName(), ex.getMessage()),
					start, null, null, task);
			if (ex instanceof AiNotConfiguredException) {
				throw ex;
			}
			throw new ModelCallFailedException("Run %s against model '%s' (%s) failed: %s".formatted(id, model.id(),
					clients.effectiveBaseUrl(model), ex.getMessage()), ex);
		}
	}

	/**
	 * Judges the run, then writes its final state. Every final state is evaluated, and final
	 * means judged: whoever waits for a run to end — a training's measurement, the matrix — must
	 * never see it done while its verdict is still missing. A judge call takes a moment; a run
	 * counted in that moment would count as failed.
	 */
	private Run finish(Run started, RunStatus status, String answer, String error, Instant start, String reportedModel,
			Integer tokens, Task task) {
		List<RunStep> trace = runs.trace(started.id());
		Integer total = tokens != null ? tokens : trace.stream()
				.map(RunStep::tokens)
				.filter(Objects::nonNull)
				.reduce(Integer::sum)
				.orElse(null);
		Run finished = started.withStatus(status, start)
				.finished(status, answer, error, reportedModel, total, Instant.now(), trace);
		try {
			return evaluation.evaluate(finished, task);
		}
		finally {
			// Last, and even if judging breaks: a run must not stay RUNNING forever.
			runs.finish(finished);
		}
	}

	public Optional<Run> find(UUID id) {
		return runs.find(id);
	}

	public List<Run> history(String taskId, String agentId, UUID batchId) {
		return runs.list(properties.getHistoryLimit(), blankToNull(taskId), blankToNull(agentId), batchId);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
