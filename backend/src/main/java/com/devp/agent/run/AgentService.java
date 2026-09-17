package com.devp.agent.run;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.devp.agent.ai.ChatClientRegistry;
import com.devp.agent.ai.ChatClientRegistry.ResolvedProvider;
import com.devp.agent.ai.ChatService.AiNotConfiguredException;
import com.devp.agent.ai.ChatService.ModelCallFailedException;
import com.devp.agent.config.AgentProperties;
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
 * Fuehrt einen Lauf aus: Auftrag rein, Schleife aus Modellrunden und Tool-Aufrufen,
 * Antwort raus — und ein vollstaendiger Trace in der Datenbank.
 *
 * <p>Die Schleife dreht Spring AIs {@code ToolCallingAdvisor}: solange das Modell Tools
 * anfordert, laesst er sie ueber den {@link TracingToolCallingManager} ausfuehren und geht
 * in die naechste Runde. Dieser Dienst setzt den Rahmen — Identitaet, Werkzeuge, Budget,
 * Aufzeichnung.
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
	private final AgentProperties properties;

	public AgentService(ChatClientRegistry providers, SoulRepository souls, TaskRepository tasks,
			SoulRenderer renderer, ToolRegistry tools, RunRepository runs, AgentProperties properties) {
		this.providers = providers;
		this.souls = souls;
		this.tasks = tasks;
		this.renderer = renderer;
		this.tools = tools;
		this.runs = runs;
		this.properties = properties;
	}

	/**
	 * Startet einen Lauf und wartet auf sein Ende.
	 *
	 * <p>Entweder {@code taskId} oder {@code goal} — eine benannte Aufgabe ist der Weg,
	 * der sich spaeter auswerten laesst, ein freier Auftrag der Weg zum Ausprobieren.
	 * Angaben am Aufruf haben Vorrang vor den Vorgaben der Aufgabe.
	 *
	 * @param goal       freier Auftrag, {@code null} wenn eine Aufgabe genannt ist
	 * @param taskId     Aufgaben-Id oder {@code null}
	 * @param providerId Provider-Id oder {@code null} fuer Aufgaben-Vorgabe bzw. Default
	 * @param soulId     SOUL-Id oder {@code null} fuer Aufgaben-Vorgabe bzw. aktive SOUL
	 */
	public AgentRun run(String goal, String taskId, String providerId, String soulId) {
		Task task = (taskId == null || taskId.isBlank()) ? null : tasks.resolve(taskId);

		if (task == null && (goal == null || goal.isBlank())) {
			throw new IllegalArgumentException("Es braucht entweder 'task' oder 'goal'.");
		}
		if (task != null) {
			goal = task.auftrag();
			soulId = leerDann(soulId, task.soul());
			providerId = leerDann(providerId, task.provider());
		}

		ResolvedProvider provider = providers.resolve(providerId);
		Soul soul = souls.resolve(soulId);

		if (!provider.config().isConfigured()) {
			throw new AiNotConfiguredException(
					"Provider '%s' ist nicht vollstaendig konfiguriert (Basis-URL, Modell und ggf. API-Key pruefen)."
							.formatted(provider.id()));
		}

		UUID id = UUID.randomUUID();
		Instant start = Instant.now();
		long zeitbudget = task != null && task.maxDurationMs() != null
				? task.maxDurationMs()
				: properties.getMaxDurationMs();
		long deadline = System.currentTimeMillis() + zeitbudget;

		AgentRun gestartet = new AgentRun(id, goal, task == null ? null : task.id(),
				task == null ? null : task.version(), soul.id(), soul.version(), provider.id(),
				provider.config().getModel(), RunStatus.RUNNING, null, null, 0, null, null, start, null, List.of());
		runs.start(gestartet);
		log.info("Lauf {} gestartet: Aufgabe {}, SOUL {} v{}, Provider {}, {} Tool(s)",
				id, task == null ? "(frei)" : task.id() + " v" + task.version(), soul.id(), soul.version(),
				provider.id(), tools.callbacks().size());

		try {
			ChatResponse antwort = provider.client().prompt()
					.system(renderer.render(soul))
					.user(goal)
					.tools(tools.callbacks())
					.toolContext(kontext(id, deadline, task))
					.call()
					.chatResponse();

			return abschliessen(gestartet, antwort, start);
		}
		catch (ToolCallLimitExceededException | BudgetExceededException ex) {
			// Budget ist ein Ergebnis, kein Fehler: der Trace bis hierher bleibt verwertbar.
			log.info("Lauf {} wegen Budget beendet: {}", id, ex.getMessage());
			return beenden(gestartet, RunStatus.BUDGET_EXCEEDED, null, ex.getMessage(), start, null);
		}
		catch (RuntimeException ex) {
			log.warn("Lauf {} fehlgeschlagen: {}", id, ex.getMessage());
			beenden(gestartet, RunStatus.FAILED, null,
					"%s: %s".formatted(ex.getClass().getSimpleName(), ex.getMessage()), start, null);
			if (ex instanceof AiNotConfiguredException || ex instanceof ModelCallFailedException) {
				throw ex;
			}
			throw new ModelCallFailedException(
					"Lauf %s gegen Provider '%s' (%s) fehlgeschlagen: %s"
							.formatted(id, provider.id(), provider.config().getBaseUrl(), ex.getMessage()),
					ex);
		}
		finally {
			log.debug("Lauf {} beendet", id);
		}
	}

	/** Was der Schleifenkoerper ueber diesen Lauf wissen muss. */
	private static Map<String, Object> kontext(UUID id, long deadline, Task task) {
		Map<String, Object> kontext = new java.util.LinkedHashMap<>();
		kontext.put(TracingToolCallingManager.RUN_ID, id);
		kontext.put(TracingToolCallingManager.DEADLINE, deadline);
		if (task != null && task.maxToolCalls() != null) {
			kontext.put(TracingToolCallingManager.MAX_TOOL_CALLS, task.maxToolCalls());
		}
		return kontext;
	}

	private static String leerDann(String wert, String ersatz) {
		return wert == null || wert.isBlank() ? ersatz : wert;
	}

	private AgentRun abschliessen(AgentRun gestartet, ChatResponse antwort, Instant start) {
		if (antwort == null || antwort.getResult() == null) {
			return beenden(gestartet, RunStatus.FAILED, null, "Provider lieferte eine leere Antwort.", start, null);
		}

		Integer tokens = antwort.getMetadata().getUsage() == null
				? null
				: antwort.getMetadata().getUsage().getTotalTokens();
		String text = antwort.getResult().getOutput().getText();

		// Abschlussrunde: die einzige Modellrunde, die keine Tools mehr anfordert und
		// deshalb nicht durch den ToolCallingManager laeuft.
		runs.addStep(gestartet.id(), new RunStep(
				runs.nextStepNumber(gestartet.id()), StepType.MODEL, antwort.getMetadata().getModel(),
				null, text, tokens, null, null, Instant.now()));

		String modell = antwort.getMetadata().getModel();
		return beenden(gestartet, RunStatus.DONE, text, null, start,
				modell == null || modell.isBlank() ? gestartet.model() : modell, tokens);
	}

	private AgentRun beenden(AgentRun gestartet, RunStatus status, String answer, String error, Instant start,
			String modell) {
		return beenden(gestartet, status, answer, error, start, modell, null);
	}

	private AgentRun beenden(AgentRun gestartet, RunStatus status, String answer, String error, Instant start,
			String modell, Integer tokens) {

		List<RunStep> trace = runs.trace(gestartet.id());
		Instant ende = Instant.now();
		Integer summe = tokens != null ? tokens : trace.stream()
				.map(RunStep::tokens)
				.filter(java.util.Objects::nonNull)
				.reduce(Integer::sum)
				.orElse(null);

		AgentRun fertig = new AgentRun(
				gestartet.id(), gestartet.goal(), gestartet.taskId(), gestartet.taskVersion(), gestartet.soulId(),
				gestartet.soulVersion(), gestartet.providerId(), modell == null ? gestartet.model() : modell,
				status, answer, error, trace.size(), summe, ende.toEpochMilli() - start.toEpochMilli(), start,
				ende, trace);

		runs.finish(fertig);
		return fertig;
	}

	public Optional<AgentRun> find(UUID id) {
		return runs.find(id);
	}

	public List<AgentRun> history() {
		return runs.list(properties.getHistoryLimit());
	}
}
