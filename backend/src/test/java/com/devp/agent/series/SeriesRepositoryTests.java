package com.devp.agent.series;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.devp.agent.run.AgentRun;
import com.devp.agent.run.RunRepository;
import com.devp.agent.run.RunStatus;
import com.devp.agent.run.RunStep;
import com.devp.agent.run.StepType;
import com.devp.agent.support.PostgresTestcontainer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestcontainer.class)
class SeriesRepositoryTests {

	@Autowired
	private SeriesRepository series;

	@Autowired
	private RunRepository runs;

	@BeforeEach
	void clear() {
		runs.deleteAll();
		series.deleteAll();
	}

	private Series created() {
		return new Series(UUID.randomUUID(), "time-and-division", 1, "forge", "baseline", "local", 3,
				SeriesStatus.RUNNING, 0, null, Instant.now(), null);
	}

	private UUID runIn(UUID seriesId, Instant startedAt) {
		UUID id = UUID.randomUUID();
		runs.start(new AgentRun(id, "goal", "time-and-division", 1, "forge", 1, "baseline", 1, seriesId, "local", "m",
				0.7, "prompt", List.of(), RunStatus.RUNNING, null, null, 0, null, null, startedAt, null, List.of(),
				List.of()));
		return id;
	}

	@Test
	void createsProgressesAndFinishes() {
		Series s = created();
		series.create(s);

		series.progress(s.id(), 2);
		assertThat(series.find(s.id()).orElseThrow().completed()).isEqualTo(2);

		series.finish(s.withProgress(3).finished(SeriesStatus.DONE, null, Instant.now()));
		Series done = series.find(s.id()).orElseThrow();
		assertThat(done.status()).isEqualTo(SeriesStatus.DONE);
		assertThat(done.completed()).isEqualTo(3);
		assertThat(done.finishedAt()).isNotNull();
		assertThat(done.soulId()).isEqualTo("forge");
	}

	/** Runs hang from the series and come back in execution order. */
	@Test
	void linksRunsInExecutionOrder() {
		Series s = created();
		series.create(s);
		Instant now = Instant.now();
		UUID first = runIn(s.id(), now.minusSeconds(20));
		UUID second = runIn(s.id(), now.minusSeconds(10));
		runIn(null, now); // a single run outside any series

		assertThat(series.runIds(s.id())).containsExactly(first, second);
		assertThat(runs.find(first).orElseThrow().seriesId()).isEqualTo(s.id());
	}

	@Test
	void countsToolCallsPerRun() {
		Series s = created();
		series.create(s);
		UUID withTools = runIn(s.id(), Instant.now().minusSeconds(5));
		UUID withoutTools = runIn(s.id(), Instant.now());
		runs.addStep(withTools, new RunStep(1, StepType.MODEL, "m", null, "x", null, null, null, Instant.now()));
		runs.addStep(withTools, new RunStep(2, StepType.TOOL, "divide", "{}", "x", null, 1L, null, Instant.now()));
		runs.addStep(withTools, new RunStep(3, StepType.TOOL, "divide", "{}", "x", null, 1L, null, Instant.now()));

		assertThat(series.toolCallsByRun(s.id()))
				.containsEntry(withTools, 2)
				.containsEntry(withoutTools, 0);
	}

	@Test
	void listsNewestFirst() {
		Series old = created();
		series.create(old);
		Series recent = new Series(UUID.randomUUID(), "no-tool", 1, "forge", "baseline", "local", 1,
				SeriesStatus.RUNNING, 0, null, Instant.now().plusSeconds(1), null);
		series.create(recent);

		assertThat(series.list(10)).extracting(Series::id).containsExactly(recent.id(), old.id());
	}

	@Test
	void unknownSeriesIsEmpty() {
		assertThat(series.find(UUID.randomUUID())).isEmpty();
	}
}
