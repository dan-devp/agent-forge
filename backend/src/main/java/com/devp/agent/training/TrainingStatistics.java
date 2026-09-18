package com.devp.agent.training;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.ToLongFunction;

import com.devp.agent.eval.DeterministicGrader;
import com.devp.agent.run.AgentRun;
import com.devp.agent.run.RunScore;

/**
 * What the N runs of a training say together — and in order.
 *
 * <p>Two success measures, because they answer different questions: {@code passAtK} — did
 * at least one run pass — measures capability and rises with N; {@code passHatK} — did every
 * run pass — measures reliability and falls with N. A task that passes sometimes is
 * {@code flaky}.
 *
 * <p>The order matters for a training: {@code timeline} lists every run with its verdict,
 * {@code firstHalfPassRate} against {@code secondHalfPassRate} says whether the second half
 * did better than the first, {@code firstPassAt} is the 1-based index of the first pass.
 * Against the flat baseline that is what learning looks like — or fails to.
 *
 * <p>The interval is Wilson at 95 %: honest for small N, where a plain proportion would
 * claim 100 % after three lucky runs.
 */
public record TrainingStatistics(
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
		Double firstHalfPassRate,
		Double secondHalfPassRate,
		Integer firstPassAt,
		List<RunPoint> timeline,
		Map<String, Integer> statusCounts,
		Aggregate tokens,
		Aggregate durationMs,
		Aggregate toolCalls) {

	private static final double Z = 1.96;

	public static TrainingStatistics of(Training training, List<AgentRun> runs, Map<UUID, Integer> toolCallsByRun) {
		int graded = 0;
		int passed = 0;
		Integer firstPassAt = null;
		Map<String, Integer> statusCounts = new TreeMap<>();
		List<RunPoint> timeline = new ArrayList<>();

		int index = 0;
		for (AgentRun run : runs) {
			index++;
			statusCounts.merge(run.status().name(), 1, Integer::sum);
			RunScore verdict = deterministic(run);
			Boolean ok = verdict == null ? null : verdict.passed();
			if (verdict != null) {
				graded++;
				if (verdict.passed()) {
					passed++;
					if (firstPassAt == null) {
						firstPassAt = index;
					}
				}
			}
			timeline.add(new RunPoint(index, run.id(), run.status().name(), ok, verdict == null ? null : verdict.score(),
					run.totalTokens(), run.durationMs(), toolCallsByRun.getOrDefault(run.id(), 0)));
		}

		double rate = graded == 0 ? 0 : (double) passed / graded;
		double[] interval = wilson(passed, graded);
		List<RunPoint> gradedPoints = timeline.stream().filter(p -> p.passed() != null).toList();
		int half = gradedPoints.size() / 2;

		return new TrainingStatistics(
				training.repetitions(),
				runs.size(),
				graded,
				passed,
				rate,
				interval[0],
				interval[1],
				passed > 0,
				graded > 0 && passed == graded,
				passed > 0 && passed < graded,
				passRate(gradedPoints.subList(0, half)),
				passRate(gradedPoints.subList(half, gradedPoints.size())),
				firstPassAt,
				List.copyOf(timeline),
				Map.copyOf(statusCounts),
				Aggregate.of(runs, r -> r.totalTokens() == null ? -1 : r.totalTokens()),
				Aggregate.of(runs, r -> r.durationMs() == null ? -1 : r.durationMs()),
				Aggregate.of(runs, r -> toolCallsByRun.getOrDefault(r.id(), 0)));
	}

	private static RunScore deterministic(AgentRun run) {
		return run.scores().stream().filter(s -> DeterministicGrader.ID.equals(s.grader())).findFirst().orElse(null);
	}

	private static Double passRate(List<RunPoint> points) {
		if (points.isEmpty()) {
			return null;
		}
		long ok = points.stream().filter(p -> Boolean.TRUE.equals(p.passed())).count();
		return (double) ok / points.size();
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
		double halfWidth = Z * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n)) / denominator;
		return new double[] { Math.max(0, centre - halfWidth), Math.min(1, centre + halfWidth) };
	}

	/**
	 * One run on the timeline.
	 *
	 * @param passed {@code null} when the run was not graded
	 * @param score  deterministic grader's share, {@code null} when not graded
	 */
	public record RunPoint(int index, UUID runId, String status, Boolean passed, Double score, Integer tokens,
			Long durationMs, int toolCalls) {
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
}
