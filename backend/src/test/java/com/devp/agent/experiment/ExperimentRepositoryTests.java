package com.devp.agent.experiment;

import java.time.Instant;
import java.util.UUID;

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

@SpringBootTest
@Import(PostgresTestcontainer.class)
class ExperimentRepositoryTests {

	@Autowired
	private ExperimentRepository experiments;

	@Autowired
	private TrainingRepository trainings;

	@BeforeEach
	void clear() {
		trainings.deleteAll();
		experiments.deleteAll();
	}

	private Experiment created(String taskId) {
		return new Experiment(UUID.randomUUID(), "Reflexion vs Baseline", taskId, 1, "forge", 1, "local", 3,
				ExperimentStatus.RUNNING, null, Instant.now(), null);
	}

	@Test
	void createsAndFinishes() {
		Experiment experiment = created("no-tool");
		experiments.create(experiment);

		assertThat(experiments.find(experiment.id()).orElseThrow().status()).isEqualTo(ExperimentStatus.RUNNING);

		experiments.finish(experiment.finished(ExperimentStatus.DONE, null, Instant.now()));

		Experiment read = experiments.find(experiment.id()).orElseThrow();
		assertThat(read.status()).isEqualTo(ExperimentStatus.DONE);
		assertThat(read.finishedAt()).isNotNull();
		assertThat(read.agentId()).isEqualTo("forge");
		assertThat(read.agentVersion()).isEqualTo(1);
		assertThat(read.repetitions()).isEqualTo(3);
	}

	/** The arms hang from the experiment and keep the order they were queued in. */
	@Test
	void listsItsArmsInStartOrder() {
		Experiment experiment = created("no-tool");
		experiments.create(experiment);
		Training baseline = arm(experiment.id(), "baseline", Instant.now());
		Training reflexion = arm(experiment.id(), "reflexion", Instant.now().plusSeconds(1));
		trainings.create(baseline);
		trainings.create(reflexion);
		trainings.create(arm(null, "baseline", Instant.now()));

		assertThat(trainings.listForExperiment(experiment.id()))
				.extracting(Training::profileId)
				.containsExactly("baseline", "reflexion");
	}

	@Test
	void listsPerTaskNewestFirst() {
		Experiment older = created("no-tool");
		experiments.create(older);
		Experiment newer = new Experiment(UUID.randomUUID(), "zweiter Versuch", "no-tool", 1, "forge", 1, "local", 3,
				ExperimentStatus.RUNNING, null, Instant.now().plusSeconds(1), null);
		experiments.create(newer);
		experiments.create(created("time-and-division"));

		assertThat(experiments.listForTask("no-tool", 10))
				.extracting(Experiment::id)
				.containsExactly(newer.id(), older.id());
		assertThat(experiments.list(10)).hasSize(3);
	}

	/** An arm waits before it runs — otherwise the UI shows a training as running that has not started. */
	@Test
	void queuedArmBecomesRunning() {
		Experiment experiment = created("no-tool");
		experiments.create(experiment);
		Training queued = arm(experiment.id(), "baseline", Instant.now());
		trainings.create(queued);

		assertThat(trainings.find(queued.id()).orElseThrow().status()).isEqualTo(TrainingStatus.QUEUED);

		trainings.markRunning(queued.id());

		assertThat(trainings.find(queued.id()).orElseThrow().status()).isEqualTo(TrainingStatus.RUNNING);
	}

	private static Training arm(UUID experimentId, String profileId, Instant createdAt) {
		return new Training(UUID.randomUUID(), experimentId, "no-tool", 1, "forge", 1, "forge", profileId, "local", 2,
				experimentId == null ? TrainingStatus.RUNNING : TrainingStatus.QUEUED, 0, null, createdAt, null);
	}
}
