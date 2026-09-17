package com.devp.agent.run;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 * Prueft die Budgetgrenzen am Schleifenkoerper. Die Tool-Ausfuehrung selbst wird nicht
 * angestossen — der Abbruch muss greifen, bevor ein Tool laeuft.
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
	void lauf() {
		runs.deleteAll();
		runId = UUID.randomUUID();
		runs.start(new AgentRun(runId, "Test", null, null, "forge", 1, "local", "mock", RunStatus.RUNNING, null, null, 0,
				null, null, Instant.now(), null, List.of()));
	}

	private static Prompt prompt(Map<String, Object> toolContext) {
		return new Prompt(List.of(), ToolCallingChatOptions.builder().toolContext(toolContext).build());
	}

	private static ChatResponse antwortMitToolAufrufen(int anzahl) {
		List<AssistantMessage.ToolCall> aufrufe = java.util.stream.IntStream.range(0, anzahl)
				.mapToObj(i -> new AssistantMessage.ToolCall("id-" + i, "function", "teilen", "{}"))
				.toList();
		return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("").toolCalls(aufrufe).build())));
	}

	private TracingToolCallingManager manager(int maxToolCalls) {
		AgentProperties budget = new AgentProperties();
		budget.setMaxToolCalls(maxToolCalls);
		budget.setMaxCallsPerTool(properties.getMaxCallsPerTool());
		return new TracingToolCallingManager(runs, budget);
	}

	/**
	 * Der Zaehler im Spring-AI-Manager sieht nur frueher ausgefuehrte Aufrufe. Fordert das
	 * Modell mehrere Tools in einer Runde an, muss trotzdem abgebrochen werden.
	 */
	@Test
	void bremstParalleleAufrufeInEinerRunde() {
		Prompt prompt = prompt(Map.of(TracingToolCallingManager.RUN_ID, runId));

		assertThatThrownBy(() -> manager(2).executeToolCalls(prompt, antwortMitToolAufrufen(3)))
				.isInstanceOf(BudgetExceededException.class)
				.hasMessageContaining("Budget von 2 Tool-Aufrufen");
	}

	/** Die ausloesende Modellrunde gehoert in den Trace, auch wenn sie abgebrochen wird. */
	@Test
	void zeichnetDieAusloesendeRundeAuf() {
		Prompt prompt = prompt(Map.of(TracingToolCallingManager.RUN_ID, runId));

		assertThatThrownBy(() -> manager(1).executeToolCalls(prompt, antwortMitToolAufrufen(2)))
				.isInstanceOf(BudgetExceededException.class);

		List<RunStep> trace = runs.trace(runId);
		assertThat(trace).hasSize(1);
		assertThat(trace.getFirst().type()).isEqualTo(StepType.MODEL);
		assertThat(trace.getFirst().output()).contains("fordert Tools an");
	}

	@Test
	void bremstNachUeberschrittenemZeitbudget() {
		Prompt prompt = prompt(Map.of(
				TracingToolCallingManager.RUN_ID, runId,
				TracingToolCallingManager.DEADLINE, System.currentTimeMillis() - 1));

		assertThatThrownBy(() -> manager(20).executeToolCalls(prompt, antwortMitToolAufrufen(1)))
				.isInstanceOf(BudgetExceededException.class)
				.hasMessageContaining("Zeitbudget");
	}

	/** Bereits ausgefuehrte Aufrufe zaehlen mit — der Trace ist die Quelle. */
	@Test
	void rechnetFruehereAufrufeAn() {
		runs.addStep(runId, new RunStep(1, StepType.TOOL, "teilen", "{}", "ok", null, 1L, null, Instant.now()));
		runs.addStep(runId, new RunStep(2, StepType.TOOL, "teilen", "{}", "ok", null, 1L, null, Instant.now()));

		Prompt prompt = prompt(Map.of(TracingToolCallingManager.RUN_ID, runId));

		assertThatThrownBy(() -> manager(2).executeToolCalls(prompt, antwortMitToolAufrufen(1)))
				.isInstanceOf(BudgetExceededException.class)
				.hasMessageContaining("bisher 2");
	}

	/** Ohne Run-Id (z. B. /api/chat) wird nichts aufgezeichnet und nichts begrenzt. */
	@Test
	void zeichnetOhneLaufNichtsAuf() {
		Prompt prompt = prompt(Map.of());

		// Ohne echte Tool-Callbacks scheitert die Ausfuehrung — entscheidend ist, dass es
		// keine BudgetExceededException ist und kein Schritt geschrieben wurde.
		assertThatThrownBy(() -> manager(0).executeToolCalls(prompt, antwortMitToolAufrufen(1)))
				.isNotInstanceOf(BudgetExceededException.class);
		assertThat(runs.trace(runId)).isEmpty();
	}
}
