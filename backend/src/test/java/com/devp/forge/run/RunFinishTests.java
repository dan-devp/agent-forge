package com.devp.forge.run;

import java.util.List;
import java.util.Map;

import com.devp.forge.eval.Eval;
import com.devp.forge.eval.EvalConfig;
import com.devp.forge.eval.EvalParam;
import com.devp.forge.eval.EvalResult;
import com.devp.forge.eval.EvalSubject;
import com.devp.forge.support.PostgresTestcontainer;
import com.devp.forge.task.Task;
import com.devp.forge.task.TaskRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A run is final only once it is judged. A training counts its runs as soon as they are all
 * final; a run marked done before its verdict was written counted as failed (5/6 where all
 * six passed). The probe below looks at the stored run while it is being judged.
 */
@SpringBootTest
@Import({ PostgresTestcontainer.class, RunFinishTests.Probe.class })
class RunFinishTests {

	private static final String TASK = "probe-" + Long.toHexString(System.nanoTime());

	@Autowired
	private RunService service;

	@Autowired
	private RunRepository runs;

	@Autowired
	private TaskRepository tasks;

	@Autowired
	private Probe probe;

	@AfterEach
	void cleanUp() {
		if (tasks.exists(TASK)) tasks.archive(TASK);
	}

	@Test
	void aRunIsStillRunningWhileItIsJudgedAndFinalOnlyWithItsVerdict() {
		tasks.create(new Task(TASK, 0, "Probe", null, null, List.of(new EvalConfig("probe", Map.of())), "Frage?"));

		// The test profile's model endpoint is dead: the run fails, is judged all the same, and the
		// synchronous caller gets the failure afterwards.
		assertThatThrownBy(() -> service.run("argus", null, TASK, null)).isInstanceOf(RuntimeException.class);

		assertThat(probe.seen).isEqualTo(RunStatus.RUNNING);
		Run stored = runs.list(1, TASK, null, null).getFirst();
		assertThat(stored.status()).isEqualTo(RunStatus.FAILED);
		assertThat(stored.passed()).isNotNull();
	}

	/** An eval that notes the stored status of the run it judges. */
	static class Probe implements Eval {

		private final RunRepository runs;
		volatile RunStatus seen;

		Probe(RunRepository runs) {
			this.runs = runs;
		}

		@Override
		public String type() {
			return "probe";
		}

		@Override
		public String label() {
			return "Probe";
		}

		@Override
		public String description() {
			return "Notes the stored run status while judging.";
		}

		@Override
		public List<EvalParam> params() {
			return List.of();
		}

		@Override
		public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
			seen = runs.list(1, TASK, null, null).getFirst().status();
			return EvalResult.pass("gesehen");
		}
	}
}
