package com.devp.agent.training;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.devp.agent.run.AgentRun;
import com.devp.agent.run.RunScore;
import com.devp.agent.run.RunStatus;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingStatisticsTests {

	private static final Training TRAINING = new Training(UUID.randomUUID(), "t", 1, "forge", "baseline", "local", 6,
			TrainingStatus.DONE, 6, null, Instant.now(), Instant.now());

	private static AgentRun run(RunStatus status, Boolean passed, Integer tokens, Long duration) {
		AgentRun run = new AgentRun(UUID.randomUUID(), "goal", "t", 1, "forge", 1, "local", "m", status, "answer", null,
				1, tokens, duration, Instant.now(), Instant.now(), List.of());
		return passed == null ? run
				: run.withScores(List.of(new RunScore("deterministic", passed ? 1.0 : 0.0, passed, "", Instant.now())));
	}

	@Test
	void countsPassedAndDistinguishesCapabilityFromReliability() {
		List<AgentRun> runs = List.of(
				run(RunStatus.DONE, true, 100, 1000L),
				run(RunStatus.DONE, false, 120, 1200L),
				run(RunStatus.DONE, true, 80, 800L));

		TrainingStatistics stats = TrainingStatistics.of(TRAINING, runs, Map.of());

		assertThat(stats.completed()).isEqualTo(3);
		assertThat(stats.graded()).isEqualTo(3);
		assertThat(stats.passed()).isEqualTo(2);
		assertThat(stats.passRate()).isCloseTo(2.0 / 3, Offset.offset(0.0001));
		assertThat(stats.passAtK()).isTrue();
		assertThat(stats.passHatK()).isFalse();
		assertThat(stats.flaky()).isTrue();
	}

	/** The order is the point of a training: fail, fail, fail, pass, pass, pass is learning; the reverse is not. */
	@Test
	void timelineShowsLearning() {
		List<AgentRun> learning = List.of(
				run(RunStatus.DONE, false, 1, 1L), run(RunStatus.DONE, false, 1, 1L), run(RunStatus.DONE, false, 1, 1L),
				run(RunStatus.DONE, true, 1, 1L), run(RunStatus.DONE, true, 1, 1L), run(RunStatus.DONE, true, 1, 1L));

		TrainingStatistics stats = TrainingStatistics.of(TRAINING, learning, Map.of());

		assertThat(stats.timeline()).extracting(TrainingStatistics.RunPoint::index).containsExactly(1, 2, 3, 4, 5, 6);
		assertThat(stats.timeline()).extracting(TrainingStatistics.RunPoint::passed)
				.containsExactly(false, false, false, true, true, true);
		assertThat(stats.firstHalfPassRate()).isEqualTo(0.0);
		assertThat(stats.secondHalfPassRate()).isEqualTo(1.0);
		assertThat(stats.firstPassAt()).isEqualTo(4);
		// Aggregate alone would say the same for the reverse order — that is why the halves exist.
		assertThat(stats.passRate()).isEqualTo(0.5);
	}

	@Test
	void ungradedRunsAreOnTheTimelineButNotInTheHalves() {
		List<AgentRun> runs = List.of(run(RunStatus.BUDGET_EXCEEDED, null, null, 5000L), run(RunStatus.DONE, true, 10, 100L));

		TrainingStatistics stats = TrainingStatistics.of(TRAINING, runs, Map.of());

		assertThat(stats.completed()).isEqualTo(2);
		assertThat(stats.graded()).isEqualTo(1);
		assertThat(stats.timeline().getFirst().passed()).isNull();
		assertThat(stats.timeline().getFirst().score()).isNull();
		assertThat(stats.firstHalfPassRate()).isNull();
		assertThat(stats.secondHalfPassRate()).isEqualTo(1.0);
		assertThat(stats.firstPassAt()).isEqualTo(2);
		assertThat(stats.statusCounts()).containsEntry("BUDGET_EXCEEDED", 1).containsEntry("DONE", 1);
	}

	@Test
	void neverPassedHasNoFirstPass() {
		TrainingStatistics stats = TrainingStatistics.of(TRAINING,
				List.of(run(RunStatus.DONE, false, 1, 1L), run(RunStatus.DONE, false, 1, 1L)), Map.of());

		assertThat(stats.firstPassAt()).isNull();
		assertThat(stats.passAtK()).isFalse();
		assertThat(stats.flaky()).isFalse();
	}

	@Test
	void aggregatesCostIncludingToolCallsPerPoint() {
		List<AgentRun> runs = List.of(run(RunStatus.DONE, true, 100, 1000L), run(RunStatus.DONE, true, 300, 3000L));
		Map<UUID, Integer> calls = Map.of(runs.get(0).id(), 2, runs.get(1).id(), 4);

		TrainingStatistics stats = TrainingStatistics.of(TRAINING, runs, calls);

		assertThat(stats.tokens().min()).isEqualTo(100);
		assertThat(stats.tokens().mean()).isEqualTo(200.0);
		assertThat(stats.tokens().max()).isEqualTo(300);
		assertThat(stats.durationMs().mean()).isEqualTo(2000.0);
		assertThat(stats.toolCalls().mean()).isEqualTo(3.0);
		assertThat(stats.timeline()).extracting(TrainingStatistics.RunPoint::toolCalls).containsExactly(2, 4);
	}

	/** Wilson: three lucky runs are not 100 % — the lower bound says so. */
	@Test
	void wilsonIntervalIsHonestForSmallN() {
		double[] three = TrainingStatistics.wilson(3, 3);
		assertThat(three[0]).isCloseTo(0.4385, Offset.offset(0.001));
		assertThat(three[1]).isEqualTo(1.0);

		double[] half = TrainingStatistics.wilson(50, 100);
		assertThat(half[0]).isCloseTo(0.4038, Offset.offset(0.001));
		assertThat(half[1]).isCloseTo(0.5962, Offset.offset(0.001));

		assertThat(TrainingStatistics.wilson(0, 0)).containsExactly(0, 0);
	}

	@Test
	void emptyTrainingHasZeroes() {
		TrainingStatistics stats = TrainingStatistics.of(TRAINING, List.of(), Map.of());

		assertThat(stats.completed()).isZero();
		assertThat(stats.passRate()).isZero();
		assertThat(stats.timeline()).isEmpty();
		assertThat(stats.firstHalfPassRate()).isNull();
		assertThat(stats.tokens().count()).isZero();
	}
}
