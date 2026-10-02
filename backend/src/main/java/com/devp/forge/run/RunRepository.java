package com.devp.forge.run;

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
import org.springframework.transaction.annotation.Transactional;

/**
 * Storage for runs, traces and verdicts.
 *
 * <p>{@link JdbcClient} instead of JPA on purpose: steps are only appended and never
 * changed, and retrieval over traces wants SQL an ORM does not help with.
 */
@Repository
public class RunRepository {

	private final JdbcClient db;

	public RunRepository(JdbcClient db) {
		this.db = db;
	}

	/** Creates the run with everything the model will see. Queued or running — a crash stays visible either way. */
	public void create(Run run) {
		db.sql("""
				insert into run (id, batch_id, task_id, task_version, agent_id, agent_version, model_id, model_version,
				                 model_name, temperature, system_prompt, input, tools, status, queued_at, started_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
				.params(run.id(), run.batchId(), run.taskId(), run.taskVersion(), run.agentId(), run.agentVersion(),
						run.modelId(), run.modelVersion(), run.modelName(), run.temperature(), run.systemPrompt(),
						run.input(), run.tools().toArray(String[]::new), run.status().name(),
						ts(run.queuedAt()), ts(run.startedAt()))
				.update();
	}

	/**
	 * QUEUED -> RUNNING, compare-and-set. Returns false when the run was cancelled in the
	 * meantime — the executor then skips it instead of reviving it.
	 */
	public boolean markRunning(UUID id, Instant startedAt) {
		return db.sql("update run set status = ?, started_at = ? where id = ? and status = ?")
				.params(RunStatus.RUNNING.name(), ts(startedAt), id, RunStatus.QUEUED.name())
				.update() == 1;
	}

	/** QUEUED -> CANCELLED. A running call is left alone: it is paid for. */
	public boolean cancel(UUID id) {
		return db.sql("update run set status = ?, finished_at = ? where id = ? and status = ?")
				.params(RunStatus.CANCELLED.name(), ts(Instant.now()), id, RunStatus.QUEUED.name())
				.update() == 1;
	}

	public int cancelBatch(UUID batchId) {
		return db.sql("update run set status = ?, finished_at = ? where batch_id = ? and status = ?")
				.params(RunStatus.CANCELLED.name(), ts(Instant.now()), batchId, RunStatus.QUEUED.name())
				.update();
	}

	public void addStep(UUID runId, RunStep step) {
		db.sql("""
				insert into run_step (run_id, seq, type, name, input, output, tokens, duration_ms, error, created_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
				.params(runId, step.seq(), step.type().name(), step.name(), step.input(), step.output(),
						step.tokens(), step.durationMs(), step.error(), ts(step.createdAt()))
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
						run.totalTokens(), run.durationMs(), ts(run.finishedAt()), run.id())
				.update();
	}

	/** Verdicts are appended one by one; the aggregate lands on the run afterwards. */
	public void addEval(UUID runId, RunEval eval) {
		db.sql("""
				insert into run_eval (run_id, idx, type, subject, passed, rationale, model, tokens, duration_ms, created_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
				.params(runId, eval.index(), eval.type(), eval.subject(), eval.passed(), eval.rationale(), eval.model(),
						eval.tokens(), eval.durationMs(), ts(eval.createdAt()))
				.update();
	}

	public void setPassed(UUID runId, Boolean passed) {
		db.sql("update run set passed = ? where id = ?").params(passed, runId).update();
	}

	public List<RunEval> evals(UUID runId) {
		return db.sql("select * from run_eval where run_id = ? order by idx, id")
				.param(runId)
				.query((rs, rowNum) -> mapEval(rs))
				.list();
	}

	/** Verdicts of every task run of one agent, by run: one query for the whole matrix, not one per run. */
	public Map<UUID, List<RunEval>> evalsByAgent(String agentId) {
		Map<UUID, List<RunEval>> byRun = new HashMap<>();
		db.sql("""
				select e.* from run_eval e join run r on r.id = e.run_id
				 where r.agent_id = ? and r.task_id is not null
				 order by e.run_id, e.idx, e.id
				""")
				.param(agentId)
				.query((ResultSet rs) -> {
					byRun.computeIfAbsent(rs.getObject("run_id", UUID.class), k -> new ArrayList<>()).add(mapEval(rs));
				});
		return byRun;
	}

	private static RunEval mapEval(ResultSet rs) throws SQLException {
		return new RunEval(
				rs.getInt("idx"),
				rs.getString("type"),
				rs.getString("subject"),
				rs.getBoolean("passed"),
				rs.getString("rationale"),
				rs.getString("model"),
				(Integer) rs.getObject("tokens"),
				(Long) rs.getObject("duration_ms"),
				rs.getTimestamp("created_at").toInstant());
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
				.map(r -> new Run(r.id(), r.batchId(), r.taskId(), r.taskVersion(), r.agentId(), r.agentVersion(),
						r.modelId(), r.modelVersion(), r.modelName(), r.temperature(), r.systemPrompt(), r.input(),
						r.tools(), r.status(), r.answer(), r.error(), r.steps(), r.totalTokens(), r.durationMs(),
						r.passed(), r.queuedAt(), r.startedAt(), r.finishedAt(), trace(id), evals(id)));
	}

	/** History without traces, prompts or inputs — the list must not load half the database. */
	public List<Run> list(int limit, String taskId, String agentId, UUID batchId) {
		return db.sql("""
				select * from run
				 where (cast(? as text) is null or task_id = ?)
				   and (cast(? as text) is null or agent_id = ?)
				   and (cast(? as uuid) is null or batch_id = ?)
				 order by coalesce(queued_at, started_at) desc
				 limit ?
				""")
				.params(taskId, taskId, agentId, agentId, batchId, batchId, limit)
				.query(RunRepository::mapRun)
				.list()
				.stream()
				.map(Run::summary)
				.toList();
	}

	/** Everything the matrix needs for one agent: one summary per task run, oldest first. */
	public List<Run> matrixRows(String agentId) {
		return db.sql("""
				select * from run
				 where agent_id = ? and task_id is not null
				 order by coalesce(queued_at, started_at), id
				""")
				.param(agentId)
				.query(RunRepository::mapRun)
				.list()
				.stream()
				.map(Run::summary)
				.toList();
	}

	public int count() {
		return db.sql("select count(*) from run").query(Integer.class).single();
	}

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
		return new Run(
				rs.getObject("id", UUID.class),
				rs.getObject("batch_id", UUID.class),
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
				instant(rs.getTimestamp("queued_at")),
				instant(rs.getTimestamp("started_at")),
				instant(rs.getTimestamp("finished_at")),
				List.of(),
				List.of());
	}

	private static Instant instant(Timestamp ts) {
		return ts == null ? null : ts.toInstant();
	}

	private static Timestamp ts(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	private static List<String> tools(Array array) throws SQLException {
		return array == null ? List.of() : List.of((String[]) array.getArray());
	}

	/** Tests and housekeeping only. */
	public void deleteAll() {
		db.sql("delete from run").update();
	}
}
