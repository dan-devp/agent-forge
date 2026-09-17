package com.devp.agent.run;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.devp.agent.config.AgentProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

/**
 * Der Schleifenkoerper des Agenten.
 *
 * <p>In Spring AI 2.0 fuehrt nicht mehr das ChatModel die Tools aus, sondern der
 * {@code ToolCallingAdvisor} im ChatClient — er ruft fuer jede Runde diesen
 * {@link ToolCallingManager}. Genau hier haengt sich der Agent ein: jede Runde wird als
 * Trace-Schritt geschrieben, und hier greift das Zeitbudget.
 *
 * <p>Die Run-Id kommt ueber den {@code toolContext} herein, weil der Manager pro
 * Modell existiert, ein Lauf aber pro Anfrage.
 */
@Component
public class TracingToolCallingManager implements ToolCallingManager {

	/** Schluessel im toolContext, unter dem der laufende Trace steckt. */
	public static final String RUN_ID = "agentRunId";

	/** Schluessel im toolContext, ab wann das Zeitbudget ueberschritten ist. */
	public static final String DEADLINE = "agentDeadline";

	/** Schluessel im toolContext fuer ein Aufrufbudget, das von der globalen Vorgabe abweicht. */
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
			// Aufruf ohne Lauf (z. B. /api/chat) — nichts aufzuzeichnen.
			return delegate.executeToolCalls(prompt, response);
		}

		AssistantMessage anfrage = response.getResult().getOutput();

		// Erst aufzeichnen, dann pruefen: die Runde ist passiert, auch wenn das Budget sie
		// stoppt. Sonst fehlt im Trace genau der Schritt, der zum Abbruch fuehrte.
		schreibeModellschritt(runId, response, anfrage);

		pruefeZeitbudget(prompt, runId);
		pruefeAufrufbudget(prompt, runId, anfrage.getToolCalls().size());

		long start = System.currentTimeMillis();
		ToolExecutionResult ergebnis;
		try {
			ergebnis = delegate.executeToolCalls(prompt, response);
		}
		catch (RuntimeException ex) {
			// Auch ein gescheiterter Tool-Aufruf gehoert in den Trace, sonst fehlt in der
			// spaeteren Auswertung genau der interessante Fall.
			anfrage.getToolCalls().forEach(call -> runs.addStep(runId, new RunStep(
					runs.nextStepNumber(runId), StepType.TOOL, call.name(), call.arguments(), null, null,
					System.currentTimeMillis() - start, ex.getMessage(), Instant.now())));
			throw ex;
		}
		long dauer = System.currentTimeMillis() - start;

		schreibeToolschritte(runId, anfrage, ergebnis, dauer);
		return ergebnis;
	}

	private void schreibeModellschritt(UUID runId, ChatResponse response, AssistantMessage anfrage) {
		Integer tokens = response.getMetadata().getUsage() == null
				? null
				: response.getMetadata().getUsage().getTotalTokens();

		String angefordert = anfrage.getToolCalls().stream()
				.map(call -> call.name() + "(" + call.arguments() + ")")
				.reduce((a, b) -> a + ", " + b)
				.orElse("");

		runs.addStep(runId, new RunStep(
				runs.nextStepNumber(runId),
				StepType.MODEL,
				response.getMetadata().getModel(),
				anfrage.getText() == null || anfrage.getText().isBlank() ? null : anfrage.getText(),
				"fordert Tools an: " + angefordert,
				tokens,
				null,
				null,
				Instant.now()));
	}

	/**
	 * Die Ergebnisse stehen am Ende des zurueckgegebenen Verlaufs. Zugeordnet wird ueber
	 * die Tool-Call-Id, nicht ueber die Reihenfolge — bei parallelen Aufrufen stimmt die
	 * Reihenfolge nicht zwingend.
	 */
	private void schreibeToolschritte(UUID runId, AssistantMessage anfrage, ToolExecutionResult ergebnis,
			long dauer) {

		Map<String, String> antworten = ergebnis.conversationHistory().stream()
				.filter(ToolResponseMessage.class::isInstance)
				.map(ToolResponseMessage.class::cast)
				.flatMap(nachricht -> nachricht.getResponses().stream())
				.collect(java.util.stream.Collectors.toMap(
						ToolResponseMessage.ToolResponse::id,
						antwort -> antwort.responseData() == null ? "" : antwort.responseData(),
						(alt, neu) -> neu));

		for (AssistantMessage.ToolCall call : anfrage.getToolCalls()) {
			runs.addStep(runId, new RunStep(
					runs.nextStepNumber(runId),
					StepType.TOOL,
					call.name(),
					call.arguments(),
					antworten.get(call.id()),
					null,
					dauer,
					null,
					Instant.now()));
			log.debug("Lauf {}: Tool {} ausgefuehrt", runId, call.name());
		}
	}

	/**
	 * Eigene Zaehlung statt nur der Grenze im {@link ToolCallingManager}: dessen Zaehler
	 * sieht nur Aufrufe aus <em>frueheren</em> Runden. Fordert das Modell in einer Runde
	 * zehn Tools parallel an, waeren die alle schon ausgefuehrt, bevor die Grenze greift.
	 */
	private void pruefeAufrufbudget(Prompt prompt, UUID runId, int angefordert) {
		// Eine Aufgabe darf ein eigenes Budget vorgeben; ohne Angabe gilt die Konfiguration.
		int grenze = toolContext(prompt).get(MAX_TOOL_CALLS) instanceof Integer vorgabe ? vorgabe : maxToolCalls;

		int bisher = runs.countToolSteps(runId);
		if (bisher + angefordert > grenze) {
			throw new BudgetExceededException(
					"Lauf %s wuerde mit %d weiteren Aufrufen das Budget von %d Tool-Aufrufen ueberschreiten (bisher %d)."
							.formatted(runId, angefordert, grenze, bisher));
		}
	}

	private void pruefeZeitbudget(Prompt prompt, UUID runId) {
		Object deadline = toolContext(prompt).get(DEADLINE);
		if (deadline instanceof Long ende && System.currentTimeMillis() > ende) {
			throw new BudgetExceededException(
					"Zeitbudget des Laufs %s aufgebraucht — Lauf abgebrochen.".formatted(runId));
		}
	}

	private static UUID runId(Prompt prompt) {
		Object wert = toolContext(prompt).get(RUN_ID);
		return wert instanceof UUID id ? id : null;
	}

	private static Map<String, Object> toolContext(Prompt prompt) {
		return prompt.getOptions() instanceof ToolCallingChatOptions options && options.getToolContext() != null
				? options.getToolContext()
				: Map.of();
	}

	/** Nur zum Lesbarhalten der Signaturen oben. */
	static List<Message> verlauf(ToolExecutionResult ergebnis) {
		return ergebnis.conversationHistory();
	}

	/** Der Lauf hat sein Zeitbudget ueberschritten. */
	public static class BudgetExceededException extends RuntimeException {
		public BudgetExceededException(String message) {
			super(message);
		}
	}
}
