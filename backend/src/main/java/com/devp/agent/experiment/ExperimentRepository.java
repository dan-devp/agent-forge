package com.devp.agent.experiment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Storage for experiments. The trainings live in {@code agent_training}, linked by
 * {@code experiment_id}; nothing about them is duplicated here.
 */
@Repository
public class ExperimentRepository {

	private final JdbcClient db;

	public ExperimentRepository(JdbcClient db) {
		this.db = db;
	}

	public void create(Experiment experiment) {
		db.sql("""
				insert into agent_experiment (id, plan_id, name, task_id, task_version, agent_id, agent_version,
				                              provider_id, repetitions, status, created_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
				.params(experiment.id(), experiment.planId(), experiment.name(), experiment.taskId(),
						experiment.taskVersion(),
						experiment.agentId(), experiment.agentVersion(), experiment.providerId(),
						experiment.repetitions(), experiment.status().name(), Timestamp.from(experiment.createdAt()))
				.update();
	}

	public void finish(Experiment experiment) {
		db.sql("update agent_experiment set status = ?, error = ?, finished_at = ? where id = ?")
				.params(experiment.status().name(), experiment.error(),
						Timestamp.from(experiment.finishedAt()), experiment.id())
				.update();
	}

	/** Executions of one plan, newest first — the time series over a configuration. */
	public List<Experiment> listForPlan(UUID planId, int limit) {
		return db.sql("select * from agent_experiment where plan_id = ? order by created_at desc limit ?")
				.params(planId, limit)
				.query(ExperimentRepository::map)
				.list();
	}

	/**
	 * Stopping is a status change, nothing more: the loop reads it before the next training. An
	 * experiment that has already finished stays as it is — the update names the state it
	 * is allowed to leave.
	 */
	public int cancel(UUID id) {
		return db.sql("update agent_experiment set status = ?, finished_at = ? where id = ? and status = ?")
				.params(ExperimentStatus.CANCELLED.name(), Timestamp.from(java.time.Instant.now()), id,
						ExperimentStatus.RUNNING.name())
				.update();
	}

	public Optional<Experiment> find(UUID id) {
		return db.sql("select * from agent_experiment where id = ?")
				.param(id)
				.query(ExperimentRepository::map)
				.optional();
	}

	public List<Experiment> list(int limit) {
		return db.sql("select * from agent_experiment order by created_at desc limit ?")
				.param(limit)
				.query(ExperimentRepository::map)
				.list();
	}

	public List<Experiment> listForTask(String taskId, int limit) {
		return db.sql("select * from agent_experiment where task_id = ? order by created_at desc limit ?")
				.params(taskId, limit)
				.query(ExperimentRepository::map)
				.list();
	}

	private static Experiment map(ResultSet rs, int rowNum) throws SQLException {
		Timestamp finished = rs.getTimestamp("finished_at");
		return new Experiment(
				rs.getObject("id", UUID.class),
				rs.getObject("plan_id", UUID.class),
				rs.getString("name"),
				rs.getString("task_id"),
				rs.getInt("task_version"),
				rs.getString("agent_id"),
				(Integer) rs.getObject("agent_version"),
				rs.getString("provider_id"),
				rs.getInt("repetitions"),
				ExperimentStatus.valueOf(rs.getString("status")),
				rs.getString("error"),
				rs.getTimestamp("created_at").toInstant(),
				finished == null ? null : finished.toInstant());
	}

	/** Tests and housekeeping only. */
	public void deleteAll() {
		db.sql("delete from agent_experiment").update();
	}
}
