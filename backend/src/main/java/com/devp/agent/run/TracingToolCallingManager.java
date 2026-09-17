package com.devp.agent.run;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.devp.agent.config.AgentProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

/**
 * The agent's loop body.
 *
 * <p>In Spring AI 2.0 the ChatModel no longer executes tools; the {@code ToolCallingAdvisor}
 * in the ChatClient does, calling this {@link ToolCallingManager} for every round. This is
 * where the agent hooks in: every round is written as a trace step, and the budget is
 * enforced here.
 *
 * <p>The run id arrives through the {@code toolContext}, because the manager exists per
 * model while a run exists per request.
 */
@Component
public class TracingToolCallingManager implements ToolCallingManager {

	/** Key in the toolContext holding the id of the run being traced. */
	public static final String RUN_ID = "agentRunId";

	/** Key in the toolContext holding the epoch millis after which the time budget is spent. */
	public static final String DEADLINE = "agentDeadline";

	/** Key in the toolContext for a call budget that deviates from the global default. */
	public static final String MAX_TOOL_CALLS = "agentMaxToolCalls";

	private static final Logger log = LoggerFactory.getLogger(TracingToolCallingManager.class);

	private final ToolCallingManager delegate;
	private final RunRepository runs;
	private final int maxToolCalls;

	public TracingToolCallingManager(RunRepository runs, AgentProperties properties) {
		this.runs = runs;
		this.maxToolCalls = properties.getMaxToolCalls();
		this.delegate = ToolCallingManager.builder()
				.maxTotalToolCalls(properties.getMaxToolCalls())
				.maxCallsPerTool(properties.getMaxCallsPerTool())
				.build();
	}

	@Override
	public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions options) {
		return delegate.resolveToolDefinitions(options);
	}

	@Override
	public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse response) {
		UUID runId = runId(prompt);
		if (runId == null) {
			// Call without a run (e.g. /api/chat) — nothing to record.
			return delegate.executeToolCalls(prompt, response);
		}

		AssistantMessage request = response.getResult().getOutput();

		// Record first, check second: the round happened even if the budget stops it.
		// Otherwise the trace lacks exactly the step that caused the abort.
		writeModelStep(runId, response, request);

		checkTimeBudget(prompt, runId);
		checkCallBudget(prompt, runId, request.getToolCalls().size());

		long start = System.currentTimeMillis();
		ToolExecutionResult result;
		try {
			result = delegate.executeToolCalls(prompt, response);
		}
		catch (RuntimeException ex) {
			// A failed tool call belongs in the trace too — otherwise the later evaluation
			// misses exactly the interesting case.
			request.getToolCalls().forEach(call -> runs.addStep(runId, new RunStep(
					runs.nextStepNumber(runId), StepType.TOOL, call.name(), call.arguments(), null, null,
					System.currentTimeMillis() - start, ex.getMessage(), Instant.now())));
			throw ex;
		}
		long duration = System.currentTimeMillis() - start;

		writeToolSteps(runId, request, result, duration);
		return result;
	}

	private void writeModelStep(UUID runId, ChatResponse response, AssistantMessage request) {
		Integer tokens = response.getMetadata().getUsage() == null
				? null
				: response.getMetadata().getUsage().getTotalTokens();

		String requested = request.getToolCalls().stream()
				.map(call -> call.name() + "(" + call.arguments() + ")")
				.reduce((a, b) -> a + ", " + b)
				.orElse("");

		runs.addStep(runId, new RunStep(
				runs.nextStepNumber(runId),
				StepType.MODEL,
				response.getMetadata().getModel(),
				request.getText() == null || request.getText().isBlank() ? null : request.getText(),
				"requests tools: " + requested,
				tokens,
				null,
				null,
				Instant.now()));
	}

	/**
	 * The results sit at the end of the returned history. They are matched by tool call id,
	 * not by position — with parallel calls the order is not guaranteed.
	 */
	private void writeToolSteps(UUID runId, AssistantMessage request, ToolExecutionResult result, long duration) {
		Map<String, String> responses = result.conversationHistory().stream()
				.filter(ToolResponseMessage.class::isInstance)
				.map(ToolResponseMessage.class::cast)
				.flatMap(message -> message.getResponses().stream())
				.collect(Collectors.toMap(
						ToolResponseMessage.ToolResponse::id,
						toolResponse -> toolResponse.responseData() == null ? "" : toolResponse.responseData(),
						(previous, next) -> next));

		for (AssistantMessage.ToolCall call : request.getToolCalls()) {
			runs.addStep(runId, new RunStep(
					runs.nextStepNumber(runId),
					StepType.TOOL,
					call.name(),
					call.arguments(),
					responses.get(call.id()),
					null,
					duration,
					null,
					Instant.now()));
			log.debug("Run {}: tool {} executed", runId, call.name());
		}
	}

	/**
	 * Own count in addition to the limit inside the {@link ToolCallingManager}: its counter
	 * only sees calls from <em>earlier</em> rounds. If the model requests ten tools in one
	 * round, all of them would already have run before the limit applies.
	 */
	private void checkCallBudget(Prompt prompt, UUID runId, int requested) {
		// A task may set its own budget; without one the configuration applies.
		int limit = toolContext(prompt).get(MAX_TOOL_CALLS) instanceof Integer override ? override : maxToolCalls;

		int soFar = runs.countToolSteps(runId);
		if (soFar + requested > limit) {
			throw new BudgetExceededException(
					"Run %s would exceed the budget of %d tool calls with %d more calls (so far: %d)."
							.formatted(runId, limit, requested, soFar));
		}
	}

	private void checkTimeBudget(Prompt prompt, UUID runId) {
		Object deadline = toolContext(prompt).get(DEADLINE);
		if (deadline instanceof Long end && System.currentTimeMillis() > end) {
			throw new BudgetExceededException(
					"Time budget of run %s is spent — run aborted.".formatted(runId));
		}
	}

	private static UUID runId(Prompt prompt) {
		Object value = toolContext(prompt).get(RUN_ID);
		return value instanceof UUID id ? id : null;
	}

	private static Map<String, Object> toolContext(Prompt prompt) {
		return prompt.getOptions() instanceof ToolCallingChatOptions options && options.getToolContext() != null
				? options.getToolContext()
				: Map.of();
	}

	/** The run exceeded its call or time budget. */
	public static class BudgetExceededException extends RuntimeException {
		public BudgetExceededException(String message) {
			super(message);
		}
	}
}
