package com.devp.forge.run;

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
 * Storage for runs, traces and verdicts.
 *
 * <p>{@link JdbcClient} instead of JPA on purpose: steps are only appended and never
 * changed, and later retrieval over traces will want SQL an ORM does not help with.
 */
@Repository
public class RunRepository {

	private final JdbcClient db;

	public RunRepository(JdbcClient db) {
		this.db = db;
	}

	/** Creates the run as soon as it begins — a crash then stays visible as RUNNING. */
	public void start(Run run) {
		db.sql("""
				insert into run (id, task_id, task_version, agent_id, agent_version, model_id, model_version,
				                 model_name, temperature, system_prompt, input, tools, status, started_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
				.params(run.id(), run.taskId(), run.taskVersion(), run.agentId(), run.agentVersion(), run.modelId(),
						run.modelVersion(), run.modelName(), run.temperature(), run.systemPrompt(), run.input(),
						run.tools().toArray(String[]::new), run.status().name(), Timestamp.from(run.startedAt()))
				.update();
	}

	public void addStep(UUID runId, RunStep step) {
		db.sql("""
				insert into run_step (run_id, seq, type, name, input, output, tokens, duration_ms, error, created_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
				.params(runId, step.seq(), step.type().name(), step.name(), step.input(), step.output(),
						step.tokens(), step.durationMs(), step.error(), Timestamp.from(step.createdAt()))
				.update();
	}

	public void finish(Run run) {
		db.sql("""
				update run
				   set status = ?, answer = ?, error = ?, model_name = ?, steps = ?,
				       total_tokens = ?, duration_ms = ?, finished_at = ?
				 where id = ?
				""")
				.params(run.status().name(), run.answer(), run.error(), run.modelName(), run.steps(),
						run.totalTokens(), run.durationMs(), Timestamp.from(run.finishedAt()), run.id())
				.update();
	}

	/** Verdicts are appended one by one; the aggregate lands on the run afterwards. */
	public void addEval(UUID runId, RunEval eval) {
		db.sql("""
				insert into run_eval (run_id, idx, type, passed, rationale, model, tokens, duration_ms, created_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
				.params(runId, eval.index(), eval.type(), eval.passed(), eval.rationale(), eval.model(),
						eval.tokens(), eval.durationMs(), Timestamp.from(eval.createdAt()))
				.update();
	}

	public void setPassed(UUID runId, Boolean passed) {
		db.sql("update run set passed = ? where id = ?").params(passed, runId).update();
	}

	public List<RunEval> evals(UUID runId) {
		return db.sql("select * from run_eval where run_id = ? order by idx")
				.param(runId)
				.query((rs, rowNum) -> new RunEval(
						rs.getInt("idx"),
						rs.getString("type"),
						rs.getBoolean("passed"),
						rs.getString("rationale"),
						rs.getString("model"),
						(Integer) rs.getObject("tokens"),
						(Long) rs.getObject("duration_ms"),
						rs.getTimestamp("created_at").toInstant()))
				.list();
	}

	/** Next free step number. The trace is the only source — no counter in memory. */
	public int nextStepNumber(UUID runId) {
		return db.sql("select coalesce(max(seq), 0) + 1 from run_step where run_id = ?")
				.param(runId)
				.query(Integer.class)
				.single();
	}

	/** Tool calls executed so far in this run — the basis of the budget check. */
	public int countToolSteps(UUID runId) {
		return db.sql("select count(*) from run_step where run_id = ? and type = ?")
				.params(runId, StepType.TOOL.name())
				.query(Integer.class)
				.single();
	}

	@Transactional(readOnly = true)
	public Optional<Run> find(UUID id) {
		return db.sql("select * from run where id = ?")
				.param(id)
				.query(RunRepository::mapRun)
				.optional()
				.map(r -> new Run(r.id(), r.taskId(), r.taskVersion(), r.agentId(), r.agentVersion(), r.modelId(),
						r.modelVersion(), r.modelName(), r.temperature(), r.systemPrompt(), r.input(), r.tools(),
						r.status(), r.answer(), r.error(), r.steps(), r.totalTokens(), r.durationMs(), r.passed(),
						r.startedAt(), r.finishedAt(), trace(id), evals(id)));
	}

	/** History without traces, prompts or inputs — the list must not load half the database. */
	public List<Run> list(int limit, String taskId, String agentId) {
		return db.sql("""
				select * from run
				 where (cast(? as text) is null or task_id = ?)
				   and (cast(? as text) is null or agent_id = ?)
				 order by started_at desc
				 limit ?
				""")
				.params(taskId, taskId, agentId, agentId, limit)
				.query(RunRepository::mapRun)
				.list()
				.stream()
				.map(Run::summary)
				.toList();
	}

	/** Do any runs reference this entity? Used before archiving, to warn rather than to block. */
	public int countByAgent(String agentId) {
		return db.sql("select count(*) from run where agent_id = ?").param(agentId).query(Integer.class).single();
	}

	public int countByTask(String taskId) {
		return db.sql("select count(*) from run where task_id = ?").param(taskId).query(Integer.class).single();
	}

	public int countByModel(String modelId) {
		return db.sql("select count(*) from run where model_id = ?").param(modelId).query(Integer.class).single();
	}

	public List<RunStep> trace(UUID runId) {
		return db.sql("select * from run_step where run_id = ? order by seq")
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

	private static Run mapRun(ResultSet rs, int rowNum) throws SQLException {
		Timestamp finished = rs.getTimestamp("finished_at");
		return new Run(
				rs.getObject("id", UUID.class),
				rs.getString("task_id"),
				(Integer) rs.getObject("task_version"),
				rs.getString("agent_id"),
				rs.getInt("agent_version"),
				rs.getString("model_id"),
				rs.getInt("model_version"),
				rs.getString("model_name"),
				// pgjdbc cannot convert numeric to Double directly.
				rs.getBigDecimal("temperature").doubleValue(),
				rs.getString("system_prompt"),
				rs.getString("input"),
				tools(rs.getArray("tools")),
				RunStatus.valueOf(rs.getString("status")),
				rs.getString("answer"),
				rs.getString("error"),
				rs.getInt("steps"),
				(Integer) rs.getObject("total_tokens"),
				(Long) rs.getObject("duration_ms"),
				(Boolean) rs.getObject("passed"),
				rs.getTimestamp("started_at").toInstant(),
				finished == null ? null : finished.toInstant(),
				List.of(),
				List.of());
	}

	private static List<String> tools(Array array) throws SQLException {
		return array == null ? List.of() : List.of((String[]) array.getArray());
	}

	/** Tests and housekeeping only. */
	public void deleteAll() {
		db.sql("delete from run").update();
	}
}
