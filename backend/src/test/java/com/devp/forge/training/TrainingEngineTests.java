package com.devp.forge.training;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

import com.devp.forge.agent.Agent;
import com.devp.forge.agent.AgentRepository;
import com.devp.forge.agent.ToolAccess;
import com.devp.forge.ai.ChatClientRegistry;
import com.devp.forge.config.RunProperties;
import com.devp.forge.config.TrainingProperties;
import com.devp.forge.model.ModelRepository;
import com.devp.forge.store.StoreExceptions.EntityConflictException;
import com.devp.forge.store.StoreExceptions.UnknownEntityException;
import com.devp.forge.support.PostgresTestcontainer;
import com.devp.forge.task.TaskRepository;
import com.devp.forge.tools.ToolRegistry;
import com.devp.forge.training.Bench.FailedRun;
import com.devp.forge.training.Bench.Measurement;
import com.devp.forge.training.Bench.TaskScore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The loop with real stores and a real database, but a fake coach and a fake bench: the bench
 * scores a prompt by its "- Regel" lines, the coach adds one per call. No run, no model call.
 */
@SpringBootTest
@Import(PostgresTestcontainer.class)
class TrainingEngineTests {

	private static final String TRAIN = "time-and-division";
	private static final String HOLDOUT = "no-tool";

	@Autowired private TrainingRepository trainings;
	@Autowired private AgentRepository agents;
	@Autowired private TaskRepository tasks;
	@Autowired private ModelRepository models;
	@Autowired private ChatClientRegistry clients;
	@Autowired private ToolRegistry tools;
	@Autowired private SessionRepository sessions;
	@Autowired private RunProperties runProperties;

	private final String suffix = Long.toHexString(System.nanoTime());
	private final String agentId = "trainee-" + suffix;
	private final String coachId = "coach-" + suffix;
	private final String trainingId = "tr-" + suffix;

	private final List<CoachBrief> briefs = new ArrayList<>();
	private final List<Integer> measured = new ArrayList<>();
	/** Per measurement: did the step already point at its batch while it was measured? */
	private final List<Boolean> visibleWhileMeasured = new ArrayList<>();
	private Function<CoachBrief, Proposal> coachBehaviour;

	@BeforeEach
	void setUp() {
		sessions.deleteAll();
		agents.create(new Agent(agentId, 0, "Trainee", null, "local", ToolAccess.ALL, "Du bist Trainee."));
		agents.create(new Agent(coachId, 0, "Coach", null, "local", ToolAccess.NONE, "Du bist Coach."));
		coachBehaviour = brief -> {
			int n = lessons(brief.systemPrompt()) + 1;
			return answer(brief.systemPrompt() + "\n- Regel " + n, "- Regel " + n + " gegen den Fehler");
		};
	}

	@AfterEach
	void cleanUp() {
		for (var store : List.of(agentId, coachId)) {
			if (agents.exists(store)) agents.archive(store);
		}
		if (trainings.exists(trainingId)) trainings.archive(trainingId);
	}

	// --- Fakes ---------------------------------------------------------------------------

	private static int lessons(String prompt) {
		return (int) prompt.lines().filter(l -> l.startsWith("- Regel")).count();
	}

	private static Proposal answer(String prompt, String rationale) {
		String raw = "BEGRUENDUNG:\n" + rationale + "\nPROMPT:\n<<<\n" + prompt + "\n>>>";
		return Proposal.parse(raw, "fake-coach", 5, 1);
	}

	/** Train k/3 = number of rules; holdout 3/3 unless the prompt learned by heart. 10 tokens per measurement. */
	private final Bench bench = new Bench() {
		@Override
		public Measurement measure(String agent, int version, List<String> train, List<String> holdout, int n,
				BooleanSupplier stop, Consumer<UUID> started) {
			measured.add(version);
			UUID batch = UUID.randomUUID();
			started.accept(batch);
			Step current = sessions.list(trainingId, 1).getFirst().steps().getLast();
			visibleWhileMeasured.add(batch.equals(current.batchId()));
			String prompt = agents.get(agent, version).systemPrompt();
			int passed = Math.min(lessons(prompt), 3);
			int held = prompt.contains("auswendig") ? 0 : 3;
			List<FailedRun> failures = passed < 3
					? List.of(new FailedRun(TRAIN, UUID.randomUUID(), "DONE", "Wie spaet?", "Mittag", null, List.of(),
							List.of("tool-called: nicht aufgerufen")))
					: List.of();
			return new Measurement(batch, new Score(passed, 3, held, 3),
					List.of(new TaskScore(TRAIN, false, passed, 3), new TaskScore(HOLDOUT, true, held, 3)), failures,
					10, stop.getAsBoolean());
		}
	};

	private final Coach coach = (c, brief) -> {
		briefs.add(brief);
		return coachBehaviour.apply(brief);
	};

	private TrainingEngine engine(Executor executor) {
		TrainingProperties fast = new TrainingProperties();
		fast.setPollMs(20);
		return new TrainingEngine(trainings, agents, tasks, models, clients, tools, sessions, coach, bench,
				runProperties, fast, executor);
	}

	private void training(int maxSteps, Integer maxTokens, Approval approval, List<String> train) {
		trainings.create(new Training(trainingId, 0, "T", null, agentId, coachId, train, List.of(HOLDOUT), 3,
				maxSteps, maxTokens, approval, null));
	}

	private Session await(TrainingEngine engine, UUID id, SessionStatus status) {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
		while (Instant.now().isBefore(deadline)) {
			Session s = engine.find(id);
			if (s.status() == status) return s;
			try {
				Thread.sleep(20);
			}
			catch (InterruptedException ex) {
				throw new IllegalStateException(ex);
			}
		}
		throw new AssertionError("session did not reach " + status + ": " + engine.find(id).status());
	}

	// --- Tests ---------------------------------------------------------------------------

	@Test
	void learnsStepByStepAndStopsWhenNothingIsLeftToLearn() {
		training(4, null, Approval.GATE, List.of(TRAIN));
		TrainingEngine engine = engine(Runnable::run);

		Session s = engine.find(engine.start(trainingId).id());

		assertThat(s.status()).isEqualTo(SessionStatus.DONE);
		assertThat(s.note()).contains("nichts mehr zu lernen");
		assertThat(s.steps()).extracting(Step::seq).containsExactly(0, 1, 2, 3);
		assertThat(s.steps()).extracting(Step::agentVersion).containsExactly(1, 2, 3, 4);
		// Every step pointed at its batch while it was still measured, not only afterwards.
		assertThat(visibleWhileMeasured).containsExactly(true, true, true, true);
		assertThat(s.steps()).extracting(Step::decision).containsOnly(Decision.ACCEPTED);
		assertThat(s.steps()).extracting(st -> st.score().train()).containsExactly("0/3", "1/3", "2/3", "3/3");
		assertThat(s.steps().get(1).baseVersion()).isEqualTo(1);
		assertThat(s.steps().get(1).decidedBy()).isEqualTo("gate");
		assertThat(s.steps().get(1).coachInput()).contains("Du bist Trainee.");
		assertThat(s.steps().get(1).rationale()).isEqualTo("- Regel 1 gegen den Fehler");
		assertThat(s.bestVersion()).isEqualTo(4);
		assertThat(s.finalVersion()).isEqualTo(4);
		// 4 measurements x 10 + 3 coach calls x 5
		assertThat(s.totalTokens()).isEqualTo(55);
		assertThat(agents.get(agentId).systemPrompt()).contains("- Regel 3");
	}

	@Test
	void rejectsWhatFitsTrainAtTheHoldoutsCostAndEndsOnTheBestPrompt() {
		training(2, null, Approval.GATE, List.of(TRAIN));
		coachBehaviour = brief -> lessons(brief.systemPrompt()) == 0
				? answer(brief.systemPrompt() + "\n- Regel 1", "- Regel 1")
				: answer(brief.systemPrompt() + "\n- Regel 2 auswendig", "- auswendig");
		TrainingEngine engine = engine(Runnable::run);

		Session s = engine.find(engine.start(trainingId).id());

		assertThat(s.status()).isEqualTo(SessionStatus.DONE);
		Step second = s.steps().get(2);
		assertThat(second.gate()).isEqualTo(Decision.REJECTED);
		assertThat(second.reason()).contains("Holdout schlechter");
		assertThat(s.bestVersion()).isEqualTo(2);
		// v3 (rejected) was on top: the best prompt comes back as v4.
		assertThat(s.finalVersion()).isEqualTo(4);
		assertThat(s.note()).contains("Prompt von v2 als v4 wieder aktuell");
		assertThat(agents.get(agentId).systemPrompt()).isEqualTo(agents.get(agentId, 2).systemPrompt());
		// The coach never saw the holdout task.
		assertThat(briefs).isNotEmpty().allSatisfy(b -> assertThat(b.render()).doesNotContain(HOLDOUT));
	}

	@Test
	void rejectedAttemptsAreShownToTheCoachNextTime() {
		training(3, null, Approval.GATE, List.of(TRAIN));
		coachBehaviour = brief -> answer(brief.systemPrompt() + "\nSei freundlich.", "- freundlicher");
		TrainingEngine engine = engine(Runnable::run);

		Session s = engine.find(engine.start(trainingId).id());

		assertThat(s.steps()).hasSize(4);
		assertThat(s.steps().subList(1, 4)).extracting(Step::decision).containsOnly(Decision.REJECTED);
		assertThat(briefs.get(2).rejected()).hasSize(2);
		assertThat(briefs.get(2).render()).contains("Bereits versucht und verworfen");
		// Every attempt starts from v1, the best; the last attempt is covered by a copy of v1.
		assertThat(briefs).allSatisfy(b -> assertThat(b.agentVersion()).isEqualTo(1));
		assertThat(s.finalVersion()).isEqualTo(5);
	}

	@Test
	void startsFromAnOlderVersionAndLeavesTheAgentAsItWasWhenNothingImproves() {
		agents.update(new Agent(agentId, 0, "Trainee", null, "local", ToolAccess.ALL, "Du bist Trainee.\n- Regel 1"));
		String trained = "Du bist Trainee.\n- Regel 1\n- Regel 2";
		agents.update(new Agent(agentId, 0, "Trainee", null, "local", ToolAccess.ALL, trained));
		training(1, null, Approval.GATE, List.of(TRAIN));
		coachBehaviour = brief -> answer(brief.systemPrompt() + "\nSei freundlich.", "- freundlicher");
		TrainingEngine engine = engine(Runnable::run);

		Session s = engine.find(engine.start(trainingId, 1).id());

		assertThat(s.startVersion()).isEqualTo(1);
		assertThat(measured).containsExactly(1, 4);
		assertThat(s.steps().get(1).decision()).isEqualTo(Decision.REJECTED);
		// Nothing beat v1, so the agent goes back to where it stood before: v3, not v1.
		assertThat(agents.get(agentId).systemPrompt()).isEqualTo(trained);
		assertThat(s.finalVersion()).isEqualTo(5);
		assertThat(s.note()).contains("Prompt von v3 als v5");
	}

	@Test
	void anUnusableAnswerIsARejectedStepWithoutANewVersion() {
		training(1, null, Approval.GATE, List.of(TRAIN));
		coachBehaviour = brief -> Proposal.parse("Ich weiss es nicht.", "fake-coach", 5, 1);
		TrainingEngine engine = engine(Runnable::run);

		Session s = engine.find(engine.start(trainingId).id());

		Step step = s.steps().get(1);
		assertThat(step.decision()).isEqualTo(Decision.REJECTED);
		assertThat(step.reason()).contains("ohne Prompt");
		assertThat(step.agentVersion()).isNull();
		assertThat(agents.versions(agentId)).hasSize(1);
		assertThat(measured).containsExactly(1);
	}

	@Test
	void endsWhenTheBudgetIsUsedUp() {
		training(5, 25, Approval.GATE, List.of(TRAIN));
		TrainingEngine engine = engine(Runnable::run);

		Session s = engine.find(engine.start(trainingId).id());

		assertThat(s.status()).isEqualTo(SessionStatus.BUDGET_EXCEEDED);
		assertThat(s.steps()).hasSize(2);
		assertThat(s.totalTokens()).isEqualTo(25);
	}

	@Test
	void manualApprovalWaitsForAHumanAndCanBeStopped() throws InterruptedException {
		training(3, null, Approval.MANUAL, List.of(TRAIN));
		TrainingEngine engine = engine(r -> new Thread(r, "test-training").start());

		UUID id = engine.start(trainingId).id();
		Session waiting = await(engine, id, SessionStatus.WAITING);
		assertThat(waiting.steps().get(1).decision()).isEqualTo(Decision.PENDING);
		assertThat(waiting.steps().get(1).gate()).isEqualTo(Decision.ACCEPTED);
		// One session at a time.
		assertThatThrownBy(() -> engine.start(trainingId)).isInstanceOf(EntityConflictException.class);

		engine.decide(id, 1, true);
		Instant deadline = Instant.now().plusSeconds(10);
		while (engine.find(id).steps().size() < 3 || engine.find(id).status() != SessionStatus.WAITING) {
			assertThat(Instant.now()).isBefore(deadline);
			Thread.sleep(20);
		}
		assertThatThrownBy(() -> engine.decide(id, 1, false)).isInstanceOf(EntityConflictException.class);

		assertThat(engine.stop(id)).isTrue();
		Session stopped = await(engine, id, SessionStatus.STOPPED);
		assertThat(stopped.steps().get(1).decidedBy()).isEqualTo("user");
		assertThat(stopped.steps().get(2).decision()).isEqualTo(Decision.REJECTED);
		assertThat(stopped.bestVersion()).isEqualTo(2);
		assertThat(stopped.finalVersion()).isEqualTo(4);
	}

	@Test
	void refusesToStartWithAMissingTaskAndFailsOrphansAfterARestart() {
		training(1, null, Approval.GATE, List.of(TRAIN, "no-such-task-" + suffix));
		TrainingEngine engine = engine(Runnable::run);
		assertThatThrownBy(() -> engine.start(trainingId)).isInstanceOf(UnknownEntityException.class);

		Session orphan = new Session(UUID.randomUUID(), trainingId, 1, agentId, coachId, 1, Approval.GATE, 1, 1, null,
				List.of(TRAIN), List.of(), SessionStatus.RUNNING, 1, 1, null, 0, null, null, Instant.now(), null,
				List.of());
		sessions.create(orphan);
		engine.failOrphans();
		assertThat(engine.find(orphan.id()).status()).isEqualTo(SessionStatus.FAILED);
		assertThat(engine.history(trainingId)).extracting(Session::id).contains(orphan.id());
	}
}
