package com.devp.agent.plan;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.devp.agent.experiment.Experiment;
import com.devp.agent.experiment.ExperimentRepository;
import com.devp.agent.experiment.ExperimentStatus;
import com.devp.agent.support.PostgresTestcontainer;
import com.devp.agent.training.Training;
import com.devp.agent.training.TrainingRepository;
import com.devp.agent.training.TrainingStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Import(PostgresTestcontainer.class)
class PlanRepositoryTests {

	@Autowired
	private PlanRepository plans;

	@Autowired
	private ExperimentRepository experiments;

	@Autowired
	private TrainingRepository trainings;

	@BeforeEach
	void clear() {
		trainings.deleteAll();
		experiments.deleteAll();
		plans.deleteAll();
	}

	private Plan plan(String name) {
		Instant now = Instant.now();
		return new Plan(UUID.randomUUID(), name, "Reflexion gegen Baseline", "no-tool", "forge", "local",
				List.of("reflexion"), true, 4, now, now);
	}

	@Test
	void storesEverythingTheStartNeeds() {
		Plan plan = plan("Reflexion auf no-tool");
		plans.create(plan);

		Plan read = plans.find(plan.id()).orElseThrow();
		assertThat(read.name()).isEqualTo("Reflexion auf no-tool");
		assertThat(read.taskId()).isEqualTo("no-tool");
		assertThat(read.agentId()).isEqualTo("forge");
		assertThat(read.profiles()).containsExactly("reflexion");
		assertThat(read.includeBaseline()).isTrue();
		assertThat(read.repetitions()).isEqualTo(4);
	}

	@Test
	void updateKeepsTheCreationTime() {
		Plan plan = plan("erst so");
		plans.create(plan);

		plans.update(new Plan(plan.id(), "dann so", null, "time-and-division", "argus", null, List.of(), false, 2,
				plan.createdAt(), Instant.now().plusSeconds(5)));

		Plan read = plans.find(plan.id()).orElseThrow();
		assertThat(read.name()).isEqualTo("dann so");
		assertThat(read.profiles()).isEmpty();
		assertThat(read.includeBaseline()).isFalse();
		// Postgres keeps microseconds, Instant nanoseconds — compare at the precision that survives.
		assertThat(read.createdAt()).isCloseTo(plan.createdAt(), within(1, ChronoUnit.MILLIS));
		assertThat(read.updatedAt()).isAfter(read.createdAt());
	}

	/** Every start is its own execution — that is what makes a plan repeatable. */
	@Test
	void listsItsExecutionsNewestFirst() {
		Plan plan = plan("zweimal gestartet");
		plans.create(plan);
		Experiment first = execution(plan.id(), Instant.now());
		Experiment second = execution(plan.id(), Instant.now().plusSeconds(1));
		experiments.create(first);
		experiments.create(second);
		experiments.create(execution(null, Instant.now()));

		assertThat(experiments.listForPlan(plan.id(), 10))
				.extracting(Experiment::id)
				.containsExactly(second.id(), first.id());
	}

	/** A measurement stays valid without its recipe — deleting the plan must not delete history. */
	@Test
	void deletingThePlanKeepsItsExperiments() {
		Plan plan = plan("wird geloescht");
		plans.create(plan);
		Experiment execution = execution(plan.id(), Instant.now());
		experiments.create(execution);

		plans.delete(plan.id());

		assertThat(plans.find(plan.id())).isEmpty();
		Experiment read = experiments.find(execution.id()).orElseThrow();
		assertThat(read.planId()).isNull();
	}

	/** Cancelling is a status change that the running loops read — and only out of RUNNING. */
	@Test
	void cancelOnlyAffectsWhatIsStillRunning() {
		Experiment running = execution(null, Instant.now());
		experiments.create(running);
		Training queued = training(running.id(), TrainingStatus.QUEUED);
		Training done = training(running.id(), TrainingStatus.DONE);
		trainings.create(queued);
		trainings.create(done);

		assertThat(experiments.cancel(running.id())).isEqualTo(1);
		assertThat(trainings.cancelPending(running.id())).isEqualTo(1);

		assertThat(experiments.find(running.id()).orElseThrow().status()).isEqualTo(ExperimentStatus.CANCELLED);
		assertThat(trainings.find(queued.id()).orElseThrow().status()).isEqualTo(TrainingStatus.CANCELLED);
		assertThat(trainings.find(done.id()).orElseThrow().status()).isEqualTo(TrainingStatus.DONE);

		// Already cancelled: nothing left to stop.
		assertThat(experiments.cancel(running.id())).isZero();
	}

	/** A training cancelled while it waited must not be revived by the loop that picks it up. */
	@Test
	void cancelledTrainingDoesNotStart() {
		Experiment experiment = execution(null, Instant.now());
		experiments.create(experiment);
		Training queued = training(experiment.id(), TrainingStatus.QUEUED);
		trainings.create(queued);

		trainings.cancel(queued.id());

		assertThat(trainings.markRunning(queued.id())).isZero();
		assertThat(trainings.status(queued.id())).contains(TrainingStatus.CANCELLED);
	}

	private static Experiment execution(UUID planId, Instant createdAt) {
		return new Experiment(UUID.randomUUID(), planId, "Lauf", "no-tool", 1, "forge", 1, "local", 2,
				ExperimentStatus.RUNNING, null, createdAt, null);
	}

	private static Training training(UUID experimentId, TrainingStatus status) {
		return new Training(UUID.randomUUID(), experimentId, "no-tool", 1, "forge", 1, "forge", "baseline", "local", 2,
				status, 0, null, Instant.now(), null);
	}
}
