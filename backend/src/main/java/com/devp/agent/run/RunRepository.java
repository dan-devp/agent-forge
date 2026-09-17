package com.devp.agent.run;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Storage for runs, traces and scores.
 *
 * <p>Deliberately {@link JdbcClient} instead of JPA: steps are only appended and never
 * changed, and step 3 will need pgvector queries where an ORM does not help.
 */
@Repository
public class RunRepository {

	private final JdbcClient db;

	public RunRepository(JdbcClient db) {
		this.db = db;
	}

	/** Creates the run as soon as it begins — a crash then stays visible as RUNNING. */
	public void start(AgentRun run) {
		db.sql("""
				insert into agent_run (id, goal, task_id, task_version, soul_id, soul_version, profile_id,
				                       profile_version, series_id, provider_id, model, temperature, system_prompt,
				                       tools, status, started_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
				.params(run.id(), run.goal(), run.taskId(), run.taskVersion(), run.soulId(), run.soulVersion(),
						run.profileId(), run.profileVersion(), run.seriesId(),
						run.providerId(), run.model(), run.temperature(), run.systemPrompt(),
						run.tools().toArray(String[]::new), run.status().name(), Timestamp.from(run.startedAt()))
				.update();
	}

	public void addStep(UUID runId, RunStep step) {
		db.sql("""
				insert into agent_run_step (run_id, seq, type, name, input, output, tokens, duration_ms, error, created_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
				.params(runId, step.seq(), step.type().name(), step.name(), step.input(), step.output(),
						step.tokens(), step.durationMs(), step.error(), Timestamp.from(step.createdAt()))
				.update();
	}

	public void finish(AgentRun run) {
		db.sql("""
				update agent_run
				   set status = ?, answer = ?, error = ?, model = ?, steps = ?,
				       total_tokens = ?, duration_ms = ?, finished_at = ?
				 where id = ?
				""")
				.params(run.status().name(), run.answer(), run.error(), run.model(), run.steps(),
						run.totalTokens(), run.durationMs(), Timestamp.from(run.finishedAt()), run.id())
				.update();
	}

	/** Scores are only appended — one record per grader, none overwrites another. */
	public void addScore(UUID runId, RunScore score) {
		db.sql("""
				insert into agent_run_score (run_id, grader, score, passed, rationale, created_at)
				values (?, ?, ?, ?, ?, ?)
				""")
				.params(runId, score.grader(), score.score(), score.passed(), score.rationale(),
						Timestamp.from(score.createdAt()))
				.update();
	}

	public List<RunScore> scores(UUID runId) {
		return db.sql("select * from agent_run_score where run_id = ? order by id")
				.param(runId)
				.query((rs, rowNum) -> new RunScore(
						rs.getString("grader"),
						rs.getDouble("score"),
						rs.getBoolean("passed"),
						rs.getString("rationale"),
						rs.getTimestamp("created_at").toInstant()))
				.list();
	}

	/** Next free step number. The trace is the only source — no counter in memory. */
	public int nextStepNumber(UUID runId) {
		return db.sql("select coalesce(max(seq), 0) + 1 from agent_run_step where run_id = ?")
				.param(runId)
				.query(Integer.class)
				.single();
	}

	/** Tool calls executed so far in this run — the basis of the budget check. */
	public int countToolSteps(UUID runId) {
		return db.sql("select count(*) from agent_run_step where run_id = ? and type = ?")
				.params(runId, StepType.TOOL.name())
				.query(Integer.class)
				.single();
	}

	@Transactional(readOnly = true)
	public Optional<AgentRun> find(UUID id) {
		Optional<AgentRun> run = db.sql("select * from agent_run where id = ?")
				.param(id)
				.query(RunRepository::mapRun)
				.optional();
		return run.map(r -> new AgentRun(r.id(), r.goal(), r.taskId(), r.taskVersion(), r.soulId(), r.soulVersion(),
				r.profileId(), r.profileVersion(), r.seriesId(), r.providerId(), r.model(), r.temperature(),
				r.systemPrompt(),
				r.tools(), r.status(), r.answer(), r.error(), r.steps(), r.totalTokens(), r.durationMs(),
				r.startedAt(), r.finishedAt(), trace(id), scores(id)));
	}

	/** History without traces — the list must not load half the database. */
	public List<AgentRun> list(int limit) {
		return db.sql("select * from agent_run order by started_at desc limit ?")
				.param(limit)
				.query(RunRepository::mapRun)
				.list();
	}

	public List<RunStep> trace(UUID runId) {
		return db.sql("select * from agent_run_step where run_id = ? order by seq")
				.param(runId)
				.query((rs, rowNum) -> new RunStep(
						rs.getInt("seq"),
						StepType.valueOf(rs.getString("type")),
						rs.getString("name"),
						rs.getString("input"),
						rs.getString("output"),
						(Integer) rs.getObject("tokens"),
						(Long) rs.getObject("duration_ms"),
						rs.getString("error"),
						rs.getTimestamp("created_at").toInstant()))
				.list();
	}

	private static AgentRun mapRun(ResultSet rs, int rowNum) throws SQLException {
		Timestamp finished = rs.getTimestamp("finished_at");
		return new AgentRun(
				rs.getObject("id", UUID.class),
				rs.getString("goal"),
				rs.getString("task_id"),
				(Integer) rs.getObject("task_version"),
				rs.getString("soul_id"),
				rs.getInt("soul_version"),
				rs.getString("profile_id"),
				(Integer) rs.getObject("profile_version"),
				rs.getObject("series_id", UUID.class),
				rs.getString("provider_id"),
				rs.getString("model"),
				temperature(rs.getBigDecimal("temperature")),
				rs.getString("system_prompt"),
				tools(rs.getArray("tools")),
				RunStatus.valueOf(rs.getString("status")),
				rs.getString("answer"),
				rs.getString("error"),
				rs.getInt("steps"),
				(Integer) rs.getObject("total_tokens"),
				(Long) rs.getObject("duration_ms"),
				rs.getTimestamp("started_at").toInstant(),
				finished == null ? null : finished.toInstant(),
				List.of(),
				List.of());
	}

	private static Double temperature(BigDecimal value) {
		return value == null ? null : value.doubleValue();
	}

	private static List<String> tools(Array array) throws SQLException {
		return array == null ? List.of() : List.of((String[]) array.getArray());
	}

	/** Tests and housekeeping only. */
	public void deleteAll() {
		db.sql("delete from agent_run").update();
	}
}
