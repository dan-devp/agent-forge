package com.devp.forge.training;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.devp.forge.doc.MarkdownDocument;
import com.devp.forge.store.EntityCodec;
import com.devp.forge.store.FileStore;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;

/**
 * <pre>
 * ---
 * id: rookie-learns
 * version: 1
 * name: Rookie lernt
 * agent: rookie
 * coach: coach
 * train: [time-and-division, no-tool]
 * holdout: [division]
 * repetitions: 3
 * max-steps: 4
 * max-tokens: 20000
 * approval: gate        # or manual
 * ---
 * Ziel fuer den Coach, in Prosa (optional).
 * </pre>
 *
 * <p>Only the shape is checked here; whether agent, coach and tasks exist is checked when
 * saving through the API and again when a session starts, because files can be edited by hand.
 */
public final class TrainingCodec implements EntityCodec<Training> {

	static final int MAX_REPETITIONS = 20;
	static final int MAX_STEPS = 20;

	@Override
	public String kind() {
		return "trainings";
	}

	@Override
	public Training decode(MarkdownDocument d) {
		String agent = FileStore.required(d, "agent");
		String coach = FileStore.required(d, "coach");
		if (agent.equals(coach)) {
			throw new EntityInvalidException("'coach' must not be the agent itself: the grader is never the student");
		}
		List<String> train = d.strings("train");
		List<String> holdout = d.strings("holdout");
		if (train.isEmpty()) {
			throw new EntityInvalidException("'train' must name at least one task");
		}
		List<String> both = train.stream().filter(holdout::contains).toList();
		if (!both.isEmpty()) {
			throw new EntityInvalidException("task(s) %s are in 'train' and 'holdout' — a holdout the coach has seen is no holdout"
					.formatted(both));
		}
		if (train.stream().distinct().count() != train.size() || holdout.stream().distinct().count() != holdout.size()) {
			throw new EntityInvalidException("'train' and 'holdout' must not name a task twice");
		}
		int repetitions = d.integer("repetitions", 3);
		if (repetitions < 1 || repetitions > MAX_REPETITIONS) {
			throw new EntityInvalidException("'repetitions' must be between 1 and " + MAX_REPETITIONS);
		}
		int maxSteps = d.integer("max-steps", 3);
		if (maxSteps < 1 || maxSteps > MAX_STEPS) {
			throw new EntityInvalidException("'max-steps' must be between 1 and " + MAX_STEPS);
		}
		Integer maxTokens = d.integer("max-tokens");
		if (maxTokens != null && maxTokens < 1) {
			throw new EntityInvalidException("'max-tokens' must be positive, or absent for no limit");
		}
		String goal = d.body();
		return new Training(
				d.id(),
				d.integer("version", 1),
				FileStore.required(d, "name"),
				d.text("description"),
				agent,
				coach,
				train,
				holdout,
				repetitions,
				maxSteps,
				maxTokens,
				Approval.of(d.text("approval")),
				goal == null || goal.isBlank() ? null : goal.strip());
	}

	@Override
	public Map<String, Object> frontMatter(Training t) {
		Map<String, Object> fm = new LinkedHashMap<>();
		fm.put("id", t.id());
		fm.put("version", t.version());
		fm.put("name", t.name());
		if (t.description() != null && !t.description().isBlank()) {
			fm.put("description", t.description());
		}
		fm.put("agent", t.agentId());
		fm.put("coach", t.coachId());
		fm.put("train", t.train());
		fm.put("holdout", t.holdout());
		fm.put("repetitions", t.repetitions());
		fm.put("max-steps", t.maxSteps());
		if (t.maxTokens() != null) {
			fm.put("max-tokens", t.maxTokens());
		}
		fm.put("approval", t.approval().key());
		return fm;
	}

	@Override
	public String body(Training t) {
		return t.goal();
	}

	@Override
	public Training withVersion(Training t, int version) {
		return new Training(t.id(), version, t.name(), t.description(), t.agentId(), t.coachId(), t.train(),
				t.holdout(), t.repetitions(), t.maxSteps(), t.maxTokens(), t.approval(), t.goal());
	}
}
