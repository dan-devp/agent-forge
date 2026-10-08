package com.devp.forge.training;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import com.devp.forge.agent.Agent;
import com.devp.forge.agent.AgentRepository;
import com.devp.forge.ai.ChatClientRegistry;
import com.devp.forge.ai.ChatClientRegistry.AiNotConfiguredException;
import com.devp.forge.config.RunProperties;
import com.devp.forge.config.TrainingProperties;
import com.devp.forge.model.Model;
import com.devp.forge.model.ModelKind;
import com.devp.forge.model.ModelRepository;
import com.devp.forge.store.StoreExceptions.EntityConflictException;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;
import com.devp.forge.task.TaskRepository;
import com.devp.forge.tools.ToolRegistry;
import com.devp.forge.training.Bench.Measurement;
import com.devp.forge.training.Bench.TaskScore;
import com.devp.forge.training.CoachBrief.Attempt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * The training loop: measure, let the coach propose, write the proposal as a new agent
 * version, measure it, decide, repeat — until the steps or the budget are used up, nothing
 * is left to learn, or someone stops it.
 *
 * <p>Every candidate becomes a real agent version, because a run must point at a recipe that
 * exists; rejected ones stay visible as rows in the matrix, which is the point — a failed
 * attempt is a finding too. The coach always improves on the <em>best</em> version, not the
 * last one. When the session ends with a rejected candidate on top, the best prompt is saved
 * once more as the newest version, so "current" means the best that was measured.
 */
@Service
public class TrainingEngine {

	private static final Logger log = LoggerFactory.getLogger(TrainingEngine.class);

	private final TrainingRepository trainings;
	private final AgentRepository agents;
	private final TaskRepository tasks;
	private final ModelRepository models;
	private final ChatClientRegistry clients;
	private final ToolRegistry tools;
	private final SessionRepository sessions;
	private final Coach coach;
	private final Bench bench;
	private final RunProperties runProperties;
	private final TrainingProperties properties;
	private final Executor executor;

	private final Set<UUID> stopRequested = ConcurrentHashMap.newKeySet();
	private volatile UUID active;

	public TrainingEngine(TrainingRepository trainings, AgentRepository agents, TaskRepository tasks,
			ModelRepository models, ChatClientRegistry clients, ToolRegistry tools, SessionRepository sessions,
			Coach coach, Bench bench, RunProperties runProperties, TrainingProperties properties,
			@Qualifier("trainingExecutor") Executor executor) {
		this.trainings = trainings;
		this.agents = agents;
		this.tasks = tasks;
		this.models = models;
		this.clients = clients;
		this.tools = tools;
		this.sessions = sessions;
		this.coach = coach;
		this.bench = bench;
		this.runProperties = runProperties;
		this.properties = properties;
		this.executor = executor;
	}

	/** A session left open by a crash or restart has no thread any more; say so instead of showing it running. */
	@EventListener(ApplicationReadyEvent.class)
	public void failOrphans() {
		int failed = sessions.failOpen("Backend restarted - the session had no thread left");
		if (failed > 0) {
			log.warn("{} training session(s) were still open after a restart and are marked FAILED", failed);
		}
	}

	// --- Commands ----------------------------------------------------------------------------

	/** From the agent's current version. */
	public Session start(String trainingId) {
		return start(trainingId, null);
	}

	/**
	 * Checks everything a session needs, records it, and hands the loop to the training thread.
	 *
	 * @param startVersion the agent version to measure and improve; {@code null} for the current
	 *                     one. An older one repeats a training from an earlier state — what the
	 *                     session writes still lands on top as the next version.
	 */
	public Session start(String trainingId, Integer startVersion) {
		Training training = trainings.get(trainingId);
		Agent current = agents.get(training.agentId());
		Agent agent = startVersion == null ? current : agents.get(training.agentId(), startVersion);
		Agent coachAgent = agents.get(training.coachId());
		check(training, agent, coachAgent);

		Session session;
		synchronized (this) {
			if (active != null) {
				throw new EntityConflictException(
						"Session %s is still running - one training at a time, they would share the run queue"
								.formatted(active));
			}
			session = new Session(UUID.randomUUID(), training.id(), training.version(), agent.id(), coachAgent.id(),
					coachAgent.version(), training.approval(), training.repetitions(), training.maxSteps(),
					training.maxTokens(), training.train(), training.holdout(), SessionStatus.RUNNING,
					agent.version(), agent.version(), null, 0, null, null, Instant.now(), null, List.of());
			sessions.create(session);
			active = session.id();
		}
		Session started = session;
		try {
			executor.execute(() -> loop(started, training, coachAgent, current.version()));
		}
		catch (RejectedExecutionException ex) {
			sessions.finish(session.id(), SessionStatus.FAILED, agent.version(), agent.version(), 0,
					"Training thread busy", null);
			active = null;
			throw new EntityConflictException("Another training session is still running.");
		}
		log.info("Training session {} started: training {} v{}, agent {} v{} (current v{}), coach {} v{}",
				session.id(), training.id(), training.version(), agent.id(), agent.version(), current.version(),
				coachAgent.id(), coachAgent.version());
		return session;
	}

	/** Asks the loop to stop at the next opportunity; queued runs of the current measurement are withdrawn. */
	public boolean stop(UUID sessionId) {
		Session s = find(sessionId);
		if (!s.status().isOpen()) {
			return false;
		}
		stopRequested.add(sessionId);
		return true;
	}

	/** A human's verdict on a candidate the session is waiting for. */
	public Step decide(UUID sessionId, int seq, boolean accept) {
		Session s = find(sessionId);
		Step step = sessions.step(sessionId, seq)
				.orElseThrow(() -> new EntityInvalidException("session %s has no step %d".formatted(sessionId, seq)));
		if (s.status() != SessionStatus.WAITING || step.decision() != Decision.PENDING) {
			throw new EntityConflictException("Step %d is not waiting for a decision.".formatted(seq));
		}
		String reason = (accept ? "Accepted by hand" : "Rejected by hand") + " (gate: " + step.reason() + ")";
		if (!sessions.decide(sessionId, seq, accept ? Decision.ACCEPTED : Decision.REJECTED, "user", reason)) {
			throw new EntityConflictException("Step %d was decided in the meantime.".formatted(seq));
		}
		return sessions.step(sessionId, seq).orElseThrow();
	}

	public Session find(UUID sessionId) {
		return sessions.find(sessionId).orElseThrow(() -> new UnknownSessionException(sessionId));
	}

	public List<Session> history(String trainingId) {
		return sessions.list(trainingId, properties.getHistoryLimit());
	}

	public boolean isOpen(String trainingId) {
		UUID id = active;
		return id != null && sessions.find(id).map(s -> s.trainingId().equals(trainingId)).orElse(false);
	}

	/**
	 * Everything that would otherwise fail minutes later, mid-session: missing tasks, a coach
	 * that writes no text, a model without key, a measurement too big for one batch.
	 */
	void check(Training training, Agent agent, Agent coachAgent) {
		training.allTasks().forEach(tasks::get);
		Model agentModel = models.get(agent.modelId());
		Model coachModel = models.get(coachAgent.modelId());
		if (coachModel.kind() != ModelKind.CHAT) {
			throw new EntityInvalidException("coach '%s' points at decision model '%s', which writes no text"
					.formatted(coachAgent.id(), coachModel.id()));
		}
		for (Model m : List.of(agentModel, coachModel)) {
			if (!clients.isConfigured(m)) {
				throw new AiNotConfiguredException("Model '%s' is not usable: %s".formatted(m.id(), clients.problem(m)));
			}
		}
		int runs = training.allTasks().size() * training.repetitions();
		if (runs > runProperties.getMaxBatchRuns()) {
			throw new EntityInvalidException("one measurement would be %d runs (%d tasks x %d), at most %d fit a batch"
					.formatted(runs, training.allTasks().size(), training.repetitions(), runProperties.getMaxBatchRuns()));
		}
	}

	// --- The loop ----------------------------------------------------------------------------

	private void loop(Session session, Training training, Agent coachAgent, int currentAtStart) {
		UUID id = session.id();
		Loop state = new Loop(session, currentAtStart);
		try {
			state.end = measureBaseline(state);
			if (state.end == null) {
				state.end = improve(state, training, coachAgent);
			}
		}
		catch (RuntimeException ex) {
			log.warn("Training session {} failed: {}", id, ex.getMessage(), ex);
			state.end = new End(SessionStatus.FAILED, null, "%s: %s".formatted(ex.getClass().getSimpleName(),
					ex.getMessage()));
		}
		finally {
			finish(state);
			stopRequested.remove(id);
			active = null;
		}
	}

	/** The step learns its batch as soon as it is queued, so its runs show while they are measured. */
	private void pointAt(Session s, StepDraft step, UUID batch) {
		step.batchId = batch;
		sessions.updateStep(s.id(), step.toStep());
	}

	/** Step 0. Returns an end when the session cannot go on. */
	private End measureBaseline(Loop state) {
		Session s = state.session;
		StepDraft step = new StepDraft(0);
		step.agentVersion = s.startVersion();
		sessions.addStep(s.id(), step.toStep());

		Measurement m = bench.measure(s.agentId(), s.startVersion(), s.train(), s.holdout(), s.repetitions(),
				() -> stopRequested.contains(s.id()), batch -> pointAt(s, step, batch));
		state.tokens += m.tokens();
		step.measured(m);
		step.decision = Decision.ACCEPTED;
		step.decidedBy = "baseline";
		step.reason = "Baseline";
		step.finishedAt = Instant.now();
		sessions.updateStep(s.id(), step.toStep());

		state.bestVersion = s.startVersion();
		state.best = m;
		sessions.progress(s.id(), SessionStatus.RUNNING, state.bestVersion, state.tokens);
		if (m.stopped()) {
			return new End(SessionStatus.STOPPED, "Stopped while measuring the baseline", null);
		}
		return null;
	}

	private End improve(Loop state, Training training, Agent coachAgent) {
		Session s = state.session;
		List<Attempt> rejected = new ArrayList<>();

		for (int seq = 1; seq <= s.maxSteps(); seq++) {
			if (stopRequested.contains(s.id())) {
				return new End(SessionStatus.STOPPED, "Stopped by hand", null);
			}
			if (overBudget(state)) {
				return budgetEnd(state);
			}
			if (state.best.score().trainPerfect()) {
				return new End(SessionStatus.DONE, "All train runs passed - nothing left to learn", null);
			}

			Agent base = agents.get(s.agentId(), state.bestVersion);
			CoachBrief brief = new CoachBrief(training.goal(), base.version(), base.systemPrompt(), toolsOf(base),
					trainScores(state.best), state.best.failures(), rejected);
			StepDraft step = new StepDraft(seq);
			step.baseVersion = base.version();
			step.coachInput = brief.render();
			sessions.addStep(s.id(), step.toStep());

			Proposal proposal = coach.propose(coachAgent, brief);
			state.tokens += proposal.tokens() == null ? 0 : proposal.tokens();
			step.coachOutput = proposal.raw();
			step.rationale = proposal.rationale();
			step.coachModel = proposal.model();
			step.coachTokens = proposal.tokens();
			step.coachDurationMs = proposal.durationMs();

			String unusable = proposal.prompt() == null ? "Coach answer without a prompt between <<< and >>>"
					: proposal.prompt().strip().equals(base.systemPrompt().strip()) ? "Proposal equals the prompt of v"
							+ base.version() : null;
			if (unusable != null) {
				step.reject("gate", unusable);
				sessions.updateStep(s.id(), step.toStep());
				rejected.add(new Attempt(null, proposal.rationale(), unusable));
				sessions.progress(s.id(), SessionStatus.RUNNING, state.bestVersion, state.tokens);
				continue;
			}
			if (overBudget(state)) {
				step.reject("gate", "Budget used up by the coach call - not measured");
				sessions.updateStep(s.id(), step.toStep());
				return budgetEnd(state);
			}

			Agent written = agents.update(new Agent(base.id(), 0, base.name(), base.description(), base.modelId(),
					base.tools(), proposal.prompt()));
			state.lastWritten = written.version();
			step.agentVersion = written.version();
			sessions.updateStep(s.id(), step.toStep());
			log.info("Session {} step {}: coach proposal written as {} v{}", s.id(), seq, written.id(),
					written.version());

			Measurement m = bench.measure(s.agentId(), written.version(), s.train(), s.holdout(), s.repetitions(),
					() -> stopRequested.contains(s.id()), batch -> pointAt(s, step, batch));
			state.tokens += m.tokens();
			step.measured(m);
			if (m.stopped()) {
				step.reject("user", "Stopped during the measurement");
				sessions.updateStep(s.id(), step.toStep());
				return new End(SessionStatus.STOPPED, "Stopped by hand", null);
			}

			Gate.Verdict gate = Gate.decide(state.best.score(), m.score());
			step.gate = gate.accepted() ? Decision.ACCEPTED : Decision.REJECTED;
			step.reason = gate.reason();
			boolean accepted;
			if (s.approval() == Approval.GATE) {
				step.decision = step.gate;
				step.decidedBy = "gate";
				step.finishedAt = Instant.now();
				sessions.updateStep(s.id(), step.toStep());
				accepted = gate.accepted();
			}
			else {
				sessions.updateStep(s.id(), step.toStep());
				sessions.progress(s.id(), SessionStatus.WAITING, state.bestVersion, state.tokens);
				Optional<Step> decided = awaitDecision(s.id(), seq);
				if (decided.isEmpty()) {
					sessions.decide(s.id(), seq, Decision.REJECTED, "user", "Session stopped without a decision");
					return new End(SessionStatus.STOPPED, "Stopped while a proposal was waiting", null);
				}
				accepted = decided.get().decision() == Decision.ACCEPTED;
				step.reason = decided.get().reason();
			}

			if (accepted) {
				state.bestVersion = written.version();
				state.best = m;
			}
			else {
				rejected.add(new Attempt(written.version(), proposal.rationale(), step.reason));
			}
			sessions.progress(s.id(), SessionStatus.RUNNING, state.bestVersion, state.tokens);
		}
		return state.best.score().trainPerfect()
				? new End(SessionStatus.DONE, "All train runs passed", null)
				: new End(SessionStatus.DONE, "All %d steps done".formatted(s.maxSteps()), null);
	}

	/** Empty when the session was stopped while waiting. */
	private Optional<Step> awaitDecision(UUID sessionId, int seq) {
		while (!stopRequested.contains(sessionId)) {
			Step step = sessions.step(sessionId, seq).orElseThrow();
			if (step.decision() != Decision.PENDING) {
				return Optional.of(step);
			}
			try {
				Thread.sleep(properties.getPollMs());
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return Optional.empty();
			}
		}
		return Optional.empty();
	}

	/**
	 * The agent ends on its best measured prompt. Only a version this session wrote is
	 * covered over — if someone saved the agent meanwhile, their version stays on top.
	 */
	private void finish(Loop state) {
		Session s = state.session;
		End end = state.end == null ? new End(SessionStatus.FAILED, null, "ended without a result") : state.end;
		String note = end.note();
		Integer finalVersion = null;
		try {
			Agent current = agents.get(s.agentId());
			// A session that improved nothing leaves the agent as it was before it began: that is
			// the start version, unless the session started from an older one on purpose.
			boolean improved = state.bestVersion != null && state.bestVersion != s.startVersion();
			Integer settle = improved ? state.bestVersion : Integer.valueOf(state.currentAtStart);
			if (state.lastWritten != null && !settle.equals(state.lastWritten) && current.version() == state.lastWritten) {
				Agent best = agents.get(s.agentId(), settle);
				current = agents.update(new Agent(best.id(), 0, best.name(), best.description(), best.modelId(),
						best.tools(), best.systemPrompt()));
				note = (note == null ? "" : note + " - ") + "Prompt of v%d current again as v%d"
						.formatted(settle, current.version());
			}
			finalVersion = current.version();
		}
		catch (RuntimeException ex) {
			log.warn("Session {}: could not settle the agent on its best version: {}", s.id(), ex.getMessage());
		}
		sessions.finish(s.id(), end.status(), state.bestVersion, finalVersion, state.tokens, end.error(), note);
		log.info("Training session {} ended {}: best v{}, final v{}, {} tokens. {}", s.id(), end.status(),
				state.bestVersion, finalVersion, state.tokens, note == null ? "" : note);
	}

	private boolean overBudget(Loop state) {
		return state.session.maxTokens() != null && state.tokens >= state.session.maxTokens();
	}

	private End budgetEnd(Loop state) {
		return new End(SessionStatus.BUDGET_EXCEEDED,
				"Budget used up: %d of %d tokens".formatted(state.tokens, state.session.maxTokens()), null);
	}

	/** What the agent is offered, as the coach should know it: name and description. */
	private List<String> toolsOf(Agent agent) {
		return tools.list().stream()
				.filter(t -> agent.tools().allows(t.name()))
				.map(t -> t.description() == null || t.description().isBlank() ? t.name()
						: t.name() + " - " + t.description().replaceAll("\\s+", " ").strip())
				.toList();
	}

	// --- State -------------------------------------------------------------------------------

	/** What one loop knows while it runs. */
	private static final class Loop {
		final Session session;
		/** The agent's current version when the session began; the start may be an older one. */
		final int currentAtStart;
		int tokens;
		Integer bestVersion;
		Measurement best;
		Integer lastWritten;
		End end;

		Loop(Session session, int currentAtStart) {
			this.session = session;
			this.currentAtStart = currentAtStart;
		}
	}

	private record End(SessionStatus status, String note, String error) {
	}

	/** A step as it grows: proposal, then measurement, then decision. */
	private static final class StepDraft {
		final int seq;
		final Instant createdAt = Instant.now();
		Integer baseVersion;
		Integer agentVersion;
		UUID batchId;
		Score score;
		Integer runTokens;
		String coachInput;
		String coachOutput;
		String rationale;
		String coachModel;
		Integer coachTokens;
		Long coachDurationMs;
		Decision gate;
		Decision decision = Decision.PENDING;
		String decidedBy;
		String reason;
		Instant finishedAt;

		StepDraft(int seq) {
			this.seq = seq;
		}

		void measured(Measurement m) {
			batchId = m.batchId();
			score = m.score();
			runTokens = m.tokens();
		}

		void reject(String by, String why) {
			decision = Decision.REJECTED;
			decidedBy = by;
			reason = why;
			finishedAt = Instant.now();
		}

		Step toStep() {
			return new Step(seq, baseVersion, agentVersion, batchId, score, runTokens, coachInput, coachOutput,
					rationale, coachModel, coachTokens, coachDurationMs, gate, decision, decidedBy, reason, createdAt,
					finishedAt);
		}
	}

	public static class UnknownSessionException extends RuntimeException {
		public UnknownSessionException(UUID id) {
			super("No training session with id " + id);
		}
	}

	/** The coach sees how the train tasks went, never the holdout. */
	static List<TaskScore> trainScores(Measurement m) {
		return m.tasks().stream().filter(t -> !t.holdout()).toList();
	}
}
