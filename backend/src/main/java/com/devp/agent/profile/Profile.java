package com.devp.agent.profile;

import java.util.List;
import java.util.Map;

/**
 * A strategy profile: which continual-learning strategies are active for a run, with
 * which parameters.
 *
 * <p>A strategy is configuration, not a code path. The profile is a versioned entity like
 * SOUL and task, and a run records which profile in which revision governed it — only then
 * can an ablation later say which strategies were active. {@code baseline} is the empty
 * profile; an ablation is a profile minus one strategy.
 *
 * @param strategies active strategies in order; empty = baseline
 * @param sections   further sections of the file, for humans
 * @param source     origin file, for provenance
 */
public record Profile(
		String id,
		String name,
		int version,
		String description,
		List<StrategyConfig> strategies,
		Map<String, String> sections,
		String source) {

	/** One active strategy with its parameters as written in the profile file. */
	public record StrategyConfig(String id, Map<String, Object> params) {

		public String param(String key) {
			Object value = params.get(key);
			return value == null ? null : String.valueOf(value);
		}

		public String param(String key, String fallback) {
			String value = param(key);
			return value == null || value.isBlank() ? fallback : value;
		}

		public int intParam(String key, int fallback) {
			Object value = params.get(key);
			if (value instanceof Number number) {
				return number.intValue();
			}
			return value == null ? fallback : Integer.parseInt(String.valueOf(value).trim());
		}
	}
}
