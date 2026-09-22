package com.devp.forge.run;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import com.devp.forge.config.RunProperties;
import com.devp.forge.run.TracingToolCallingManager.BudgetExceededException;
import com.devp.forge.support.PostgresTestcontainer;

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

	private UUID runId;

	@BeforeEach
	void startRun() {
		runs.deleteAll();
		runId = UUID.randomUUID();
		runs.create(RunRepositoryTests.started(runId));
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
		RunProperties budget = new RunProperties();
		budget.setMaxToolCalls(maxToolCalls);
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

	/** The model round that triggered the abort is in the trace anyway. */
	@Test
	void recordsTheModelRoundBeforeAborting() {
		Prompt prompt = prompt(Map.of(TracingToolCallingManager.RUN_ID, runId));
		assertThatThrownBy(() -> manager(1).executeToolCalls(prompt, responseWithToolCalls(2)))
				.isInstanceOf(BudgetExceededException.class);

		List<RunStep> trace = runs.trace(runId);
		assertThat(trace).hasSize(1);
		assertThat(trace.getFirst().type()).isEqualTo(StepType.MODEL);
		assertThat(trace.getFirst().output()).contains("divide({})");
	}

	@Test
	void aTaskMayLowerTheBudget() {
		Prompt prompt = prompt(Map.of(TracingToolCallingManager.RUN_ID, runId,
				TracingToolCallingManager.MAX_TOOL_CALLS, 1));

		assertThatThrownBy(() -> manager(20).executeToolCalls(prompt, responseWithToolCalls(2)))
				.isInstanceOf(BudgetExceededException.class)
				.hasMessageContaining("budget of 1 tool calls");
	}

	@Test
	void stopsWhenTheDeadlineHasPassed() {
		Prompt prompt = prompt(Map.of(TracingToolCallingManager.RUN_ID, runId,
				TracingToolCallingManager.DEADLINE, System.currentTimeMillis() - 1));

		assertThatThrownBy(() -> manager(20).executeToolCalls(prompt, responseWithToolCalls(1)))
				.isInstanceOf(BudgetExceededException.class)
				.hasMessageContaining("Time budget");
	}

	/** Without a run id nothing is recorded — but the call also must not be blocked by our budget. */
	@Test
	void ignoresCallsWithoutARun() {
		Prompt prompt = prompt(Map.of());
		// The delegate has no tool callbacks to execute and complains about that — but not
		// with our budget exception, and nothing is written.
		assertThatThrownBy(() -> manager(0).executeToolCalls(prompt, responseWithToolCalls(1)))
				.isNotInstanceOf(BudgetExceededException.class);
		assertThat(runs.trace(runId)).isEmpty();
		assertThat(Instant.now()).isNotNull();
	}
}
