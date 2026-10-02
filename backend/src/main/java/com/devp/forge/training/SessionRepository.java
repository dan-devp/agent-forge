package com.devp.forge.training;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Storage for training sessions and their steps; {@link JdbcClient} like the runs. */
@Repository
public class SessionRepository {

	private final JdbcClient db;

	public SessionRepository(JdbcClient db) {
		this.db = db;
	}

	public void create(Session s) {
		db.sql("""
				insert into training_session (id, training_id, training_version, agent_id, coach_id, coach_version,
				                              approval, repetitions, max_steps, max_tokens, train_tasks, holdout_tasks,
				                              status, start_version, best_version, total_tokens, started_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
				.params(s.id(), s.trainingId(), s.trainingVersion(), s.agentId(), s.coachId(), s.coachVersion(),
						s.approval().name(), s.repetitions(), s.maxSteps(), s.maxTokens(),
						s.train().toArray(String[]::new), s.holdout().toArray(String[]::new), s.status().name(),
						s.startVersion(), s.bestVersion(), s.totalTokens(), ts(s.startedAt()))
				.update();
	}

	/** Status, best version and cost while the session runs. */
	public void progress(UUID id, SessionStatus status, Integer bestVersion, int totalTokens) {
		db.sql("update training_session set status = ?, best_version = ?, total_tokens = ? where id = ?")
				.params(status.name(), bestVersion, totalTokens, id)
				.update();
	}

	public void finish(UUID id, SessionStatus status, Integer bestVersion, Integer finalVersion, int totalTokens,
			String error, String note) {
		db.sql("""
				update training_session
				   set status = ?, best_version = ?, final_version = ?, total_tokens = ?, error = ?, note = ?,
				       finished_at = ?
				 where id = ?
				""")
				.params(status.name(), bestVersion, finalVersion, totalTokens, error, note, ts(Instant.now()), id)
				.update();
	}

	/** Sessions still open after a restart have lost their thread; they end as FAILED. */
	public int failOpen(String note) {
		db.sql("update training_step set decision = ?, reason = ? where decision = ? and session_id in "
				+ "(select id from training_session where status in (?, ?))")
				.params(Decision.REJECTED.name(), note, Decision.PENDING.name(), SessionStatus.RUNNING.name(),
						SessionStatus.WAITING.name())
				.update();
		return db.sql("update training_session set status = ?, error = ?, finished_at = ? where status in (?, ?)")
				.params(SessionStatus.FAILED.name(), note, ts(Instant.now()), SessionStatus.RUNNING.name(),
						SessionStatus.WAITING.name())
				.update();
	}

	public void addStep(UUID sessionId, Step step) {
		db.sql("""
				insert into training_step (session_id, seq, base_version, agent_version, batch_id, train_passed,
				                           train_total, holdout_passed, holdout_total, run_tokens, coach_input,
				                           coach_output, rationale, coach_model, coach_tokens, coach_duration_ms,
				                           gate, decision, decided_by, reason, created_at, finished_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
				.params(sessionId, step.seq(), step.baseVersion(), step.agentVersion(), step.batchId(),
						passed(step.score(), true), total(step.score(), true), passed(step.score(), false),
						total(step.score(), false), step.runTokens(), step.coachInput(), step.coachOutput(),
						step.rationale(), step.coachModel(), step.coachTokens(), step.coachDurationMs(),
						name(step.gate()), step.decision().name(), step.decidedBy(), step.reason(),
						ts(step.createdAt()), ts(step.finishedAt()))
				.update();
	}

	/** Rewrites a step by session and seq; the step grows from proposal to measurement to decision. */
	public void updateStep(UUID sessionId, Step step) {
		db.sql("""
				update training_step
				   set base_version = ?, agent_version = ?, batch_id = ?, train_passed = ?, train_total = ?,
				       holdout_passed = ?, holdout_total = ?, run_tokens = ?, coach_input = ?, coach_output = ?,
				       rationale = ?, coach_model = ?, coach_tokens = ?, coach_duration_ms = ?, gate = ?,
				       decision = ?, decided_by = ?, reason = ?, finished_at = ?
				 where session_id = ? and seq = ?
				""")
				.params(step.baseVersion(), step.agentVersion(), step.batchId(), passed(step.score(), true),
						total(step.score(), true), passed(step.score(), false), total(step.score(), false),
						step.runTokens(), step.coachInput(), step.coachOutput(), step.rationale(), step.coachModel(),
						step.coachTokens(), step.coachDurationMs(), name(step.gate()), step.decision().name(),
						step.decidedBy(), step.reason(), ts(step.finishedAt()), sessionId, step.seq())
				.update();
	}

	/**
	 * PENDING -> ACCEPTED/REJECTED, compare-and-set: a decision is taken once, and a click on a
	 * step that is no longer waiting changes nothing.
	 */
	public boolean decide(UUID sessionId, int seq, Decision decision, String decidedBy, String reason) {
		return db.sql("""
				update training_step set decision = ?, decided_by = ?, reason = ?, finished_at = ?
				 where session_id = ? and seq = ? and decision = ?
				""")
				.params(decision.name(), decidedBy, reason, ts(Instant.now()), sessionId, seq, Decision.PENDING.name())
				.update() == 1;
	}

	public Optional<Step> step(UUID sessionId, int seq) {
		return db.sql("select * from training_step where session_id = ? and seq = ?")
				.params(sessionId, seq)
				.query((rs, n) -> mapStep(rs, true))
				.optional();
	}

	/** With every step, including what the coach saw and said. */
	public Optional<Session> find(UUID id) {
		return db.sql("select * from training_session where id = ?")
				.param(id)
				.query(SessionRepository::mapSession)
				.optional()
				.map(s -> s.withSteps(db.sql("select * from training_step where session_id = ? order by seq")
						.param(id)
						.query((rs, n) -> mapStep(rs, true))
						.list()));
	}

	/**
	 * Newest first, steps without the coach's texts — enough for the curve. {@code trainingId}
	 * {@code null} lists every training.
	 */
	public List<Session> list(String trainingId, int limit) {
		List<Session> sessions = db.sql("""
				select * from training_session
				 where (cast(? as text) is null or training_id = ?)
				 order by started_at desc
				 limit ?
				""")
				.params(trainingId, trainingId, limit)
				.query(SessionRepository::mapSession)
				.list();
		if (sessions.isEmpty()) {
			return sessions;
		}
		Map<UUID, List<Step>> steps = new HashMap<>();
		db.sql("select * from training_step where session_id = any(?) order by session_id, seq")
				.param(sessions.stream().map(Session::id).toArray(UUID[]::new))
				.query((ResultSet rs) -> {
					steps.computeIfAbsent(rs.getObject("session_id", UUID.class), k -> new ArrayList<>())
							.add(mapStep(rs, false));
				});
		return sessions.stream().map(s -> s.withSteps(steps.getOrDefault(s.id(), List.of()))).toList();
	}

	public int countByTraining(String trainingId) {
		return db.sql("select count(*) from training_session where training_id = ?")
				.param(trainingId)
				.query(Integer.class)
				.single();
	}

	/** Tests and housekeeping only. */
	public void deleteAll() {
		db.sql("delete from training_session").update();
	}

	private static Session mapSession(ResultSet rs, int rowNum) throws SQLException {
		return new Session(
				rs.getObject("id", UUID.class),
				rs.getString("training_id"),
				rs.getInt("training_version"),
				rs.getString("agent_id"),
				rs.getString("coach_id"),
				rs.getInt("coach_version"),
				Approval.valueOf(rs.getString("approval")),
				rs.getInt("repetitions"),
				rs.getInt("max_steps"),
				(Integer) rs.getObject("max_tokens"),
				strings(rs.getArray("train_tasks")),
				strings(rs.getArray("holdout_tasks")),
				SessionStatus.valueOf(rs.getString("status")),
				rs.getInt("start_version"),
				(Integer) rs.getObject("best_version"),
				(Integer) rs.getObject("final_version"),
				rs.getInt("total_tokens"),
				rs.getString("error"),
				rs.getString("note"),
				instant(rs.getTimestamp("started_at")),
				instant(rs.getTimestamp("finished_at")),
				List.of());
	}

	private static Step mapStep(ResultSet rs, boolean texts) throws SQLException {
		Integer trainTotal = (Integer) rs.getObject("train_total");
		Score score = trainTotal == null ? null
				: new Score(rs.getInt("train_passed"), trainTotal, rs.getInt("holdout_passed"),
						rs.getInt("holdout_total"));
		String gate = rs.getString("gate");
		return new Step(
				rs.getInt("seq"),
				(Integer) rs.getObject("base_version"),
				(Integer) rs.getObject("agent_version"),
				rs.getObject("batch_id", UUID.class),
				score,
				(Integer) rs.getObject("run_tokens"),
				texts ? rs.getString("coach_input") : null,
				texts ? rs.getString("coach_output") : null,
				rs.getString("rationale"),
				rs.getString("coach_model"),
				(Integer) rs.getObject("coach_tokens"),
				(Long) rs.getObject("coach_duration_ms"),
				gate == null ? null : Decision.valueOf(gate),
				Decision.valueOf(rs.getString("decision")),
				rs.getString("decided_by"),
				rs.getString("reason"),
				instant(rs.getTimestamp("created_at")),
				instant(rs.getTimestamp("finished_at")));
	}

	private static Integer passed(Score s, boolean train) {
		return s == null ? null : train ? s.trainPassed() : s.holdoutPassed();
	}

	private static Integer total(Score s, boolean train) {
		return s == null ? null : train ? s.trainTotal() : s.holdoutTotal();
	}

	private static String name(Enum<?> e) {
		return e == null ? null : e.name();
	}

	private static List<String> strings(Array array) throws SQLException {
		return array == null ? List.of() : List.of((String[]) array.getArray());
	}

	private static Instant instant(Timestamp ts) {
		return ts == null ? null : ts.toInstant();
	}

	private static Timestamp ts(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}
}
