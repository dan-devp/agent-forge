package com.devp.agent.run;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import com.devp.agent.config.AgentProperties;
import com.devp.agent.run.TracingToolCallingManager.BudgetExceededException;
import com.devp.agent.support.PostgresTestcontainer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks the budget limits at the loop body. Tool execution itself is not triggered — the
 * abort must happen before any tool runs.
 */
@SpringBootTest
@Import(PostgresTestcontainer.class)
class TracingToolCallingManagerTests {

	@Autowired
	private RunRepository runs;

	@Autowired
	private AgentProperties properties;

	private UUID runId;

	@BeforeEach
	void startRun() {
		runs.deleteAll();
		runId = UUID.randomUUID();
		runs.start(new AgentRun(runId, "Test", null, null, "forge", 1, "local", "mock", RunStatus.RUNNING, null, null, 0,
				null, null, Instant.now(), null, List.of()));
	}

	private static Prompt prompt(Map<String, Object> toolContext) {
		return new Prompt(List.of(), ToolCallingChatOptions.builder().toolContext(toolContext).build());
	}

	private static ChatResponse responseWithToolCalls(int count) {
		List<AssistantMessage.ToolCall> calls = IntStream.range(0, count)
				.mapToObj(i -> new AssistantMessage.ToolCall("id-" + i, "function", "divide", "{}"))
				.toList();
		return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("").toolCalls(calls).build())));
	}

	private TracingToolCallingManager manager(int maxToolCalls) {
		AgentProperties budget = new AgentProperties();
		budget.setMaxToolCalls(maxToolCalls);
		budget.setMaxCallsPerTool(properties.getMaxCallsPerTool());
		return new TracingToolCallingManager(runs, budget);
	}

	/**
	 * The counter inside the Spring AI manager only sees previously executed calls. If the
	 * model requests several tools in one round, the run must still be aborted.
	 */
	@Test
	void stopsParallelCallsWithinOneRound() {
		Prompt prompt = prompt(Map.of(TracingToolCallingManager.RUN_ID, runId));

		assertThatThrownBy(() -> manager(2).executeToolCalls(prompt, responseWithToolCalls(3)))
				.isInstanceOf(BudgetExceededException.class)
				.hasMessageContaining("budget of 2 tool calls");
	}

	/** The triggering model round belongs in the trace even if it is aborted. */
	@Test
	void recordsTheTriggeringRound() {
		Prompt prompt = prompt(Map.of(TracingToolCallingManager.RUN_ID, runId));

		assertThatThrownBy(() -> manager(1).executeToolCalls(prompt, responseWithToolCalls(2)))
				.isInstanceOf(BudgetExceededException.class);

		List<RunStep> trace = runs.trace(runId);
		assertThat(trace).hasSize(1);
		assertThat(trace.getFirst().type()).isEqualTo(StepType.MODEL);
		assertThat(trace.getFirst().output()).contains("requests tools");
	}

	@Test
	void stopsAfterExceededTimeBudget() {
		Prompt prompt = prompt(Map.of(
				TracingToolCallingManager.RUN_ID, runId,
				TracingToolCallingManager.DEADLINE, System.currentTimeMillis() - 1));

		assertThatThrownBy(() -> manager(20).executeToolCalls(prompt, responseWithToolCalls(1)))
				.isInstanceOf(BudgetExceededException.class)
				.hasMessageContaining("Time budget");
	}

	/** Previously executed calls count — the trace is the source. */
	@Test
	void countsEarlierCalls() {
		runs.addStep(runId, new RunStep(1, StepType.TOOL, "divide", "{}", "ok", null, 1L, null, Instant.now()));
		runs.addStep(runId, new RunStep(2, StepType.TOOL, "divide", "{}", "ok", null, 1L, null, Instant.now()));

		Prompt prompt = prompt(Map.of(TracingToolCallingManager.RUN_ID, runId));

		assertThatThrownBy(() -> manager(2).executeToolCalls(prompt, responseWithToolCalls(1)))
				.isInstanceOf(BudgetExceededException.class)
				.hasMessageContaining("so far: 2");
	}

	/** Without a run id (e.g. /api/chat) nothing is recorded and nothing is limited. */
	@Test
	void recordsNothingWithoutRun() {
		Prompt prompt = prompt(Map.of());

		// Without real tool callbacks the execution fails — what matters is that it is not a
		// BudgetExceededException and that no step was written.
		assertThatThrownBy(() -> manager(0).executeToolCalls(prompt, responseWithToolCalls(1)))
				.isNotInstanceOf(BudgetExceededException.class);
		assertThat(runs.trace(runId)).isEmpty();
	}
}
