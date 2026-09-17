package com.devp.agent.series;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToLongFunction;

import com.devp.agent.eval.DeterministicGrader;
import com.devp.agent.run.AgentRun;
import com.devp.agent.run.RunScore;
import com.devp.agent.run.RunStatus;

/**
 * What N runs of one task say together. Computed from the runs on every read.
 *
 * <p>Two success measures, because they answer different questions: {@code passAtK} —
 * did at least one run pass — measures capability and rises with N; {@code passHatK} — did
 * every run pass — measures reliability and falls with N. A task that passes sometimes is
 * {@code flaky}; the literature finds variance concentrated in exactly such tasks, so they
 * are worth listing by name rather than averaging away.
 *
 * <p>The interval is Wilson at 95 %: honest for small N, where a plain proportion would
 * claim 100 % after three lucky runs.
 *
 * @param passed   runs the grader marked as passed (deterministic grader; others do not count here yet)
 * @param passRate passed / graded, 0 when nothing was graded
 * @param cost     tokens, duration and tool calls per run, aggregated
 */
public record SeriesStatistics(
		int repetitions,
		int completed,
		int graded,
		int passed,
		double passRate,
		double passRateLower,
		double passRateUpper,
		boolean passAtK,
		boolean passHatK,
		boolean flaky,
		Map<String, Integer> statusCounts,
		Aggregate tokens,
		Aggregate durationMs,
		Aggregate toolCalls) {

	private static final double Z = 1.96;

	public static SeriesStatistics of(Series series, List<AgentRun> runs, Map<java.util.UUID, Integer> toolCallsByRun) {
		int completed = runs.size();
		int graded = 0;
		int passed = 0;
		Map<String, Integer> statusCounts = new TreeMap<>();
		for (AgentRun run : runs) {
			statusCounts.merge(run.status().name(), 1, Integer::sum);
			for (RunScore score : run.scores()) {
				if (DeterministicGrader.ID.equals(score.grader())) {
					graded++;
					if (score.passed()) {
						passed++;
					}
				}
			}
		}

		double rate = graded == 0 ? 0 : (double) passed / graded;
		double[] interval = wilson(passed, graded);

		return new SeriesStatistics(
				series.repetitions(),
				completed,
				graded,
				passed,
				rate,
				interval[0],
				interval[1],
				passed > 0,
				graded > 0 && passed == graded,
				passed > 0 && passed < graded,
				Map.copyOf(statusCounts),
				Aggregate.of(runs, r -> r.totalTokens() == null ? -1 : r.totalTokens()),
				Aggregate.of(runs, r -> r.durationMs() == null ? -1 : r.durationMs()),
				Aggregate.of(runs, r -> toolCallsByRun.getOrDefault(r.id(), 0)));
	}

	/** Wilson score interval; {0, 0} without observations. */
	static double[] wilson(int successes, int n) {
		if (n == 0) {
			return new double[] { 0, 0 };
		}
		double p = (double) successes / n;
		double z2 = Z * Z;
		double denominator = 1 + z2 / n;
		double centre = (p + z2 / (2 * n)) / denominator;
		double half = Z * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n)) / denominator;
		return new double[] { Math.max(0, centre - half), Math.min(1, centre + half) };
	}

	/** Min, mean and max over the runs that have the value; {@code count} says how many did. */
	public record Aggregate(int count, long min, double mean, long max) {

		static Aggregate of(List<AgentRun> runs, ToLongFunction<AgentRun> value) {
			long[] values = runs.stream().mapToLong(value).filter(v -> v >= 0).toArray();
			if (values.length == 0) {
				return new Aggregate(0, 0, 0, 0);
			}
			long min = Long.MAX_VALUE;
			long max = Long.MIN_VALUE;
			long sum = 0;
			for (long v : values) {
				min = Math.min(min, v);
				max = Math.max(max, v);
				sum += v;
			}
			return new Aggregate(values.length, min, (double) sum / values.length, max);
		}
	}

	/** Convenience for the frontend: how the series ended per run status. */
	public int count(RunStatus status) {
		return statusCounts.getOrDefault(status.name(), 0);
	}
}
