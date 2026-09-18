package com.devp.agent.training;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.devp.agent.run.StepType;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Storage for trainings and their memory. The runs live in {@code agent_run}, linked by {@code training_id}. */
@Repository
public class TrainingRepository {

	private final JdbcClient db;

	public TrainingRepository(JdbcClient db) {
		this.db = db;
	}

	public void create(Training training) {
		db.sql("""
				insert into agent_training (id, task_id, task_version, soul_id, profile_id, provider_id, repetitions,
				                            status, completed, created_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
				.params(training.id(), training.taskId(), training.taskVersion(), training.soulId(),
						training.profileId(), training.providerId(), training.repetitions(), training.status().name(),
						training.completed(), Timestamp.from(training.createdAt()))
				.update();
	}

	public void progress(UUID id, int completed) {
		db.sql("update agent_training set completed = ? where id = ?").params(completed, id).update();
	}

	public void finish(Training training) {
		db.sql("update agent_training set status = ?, error = ?, completed = ?, finished_at = ? where id = ?")
				.params(training.status().name(), training.error(), training.completed(),
						Timestamp.from(training.finishedAt()), training.id())
				.update();
	}

	public Optional<Training> find(UUID id) {
		return db.sql("select * from agent_training where id = ?").param(id).query(TrainingRepository::map).optional();
	}

	public List<Training> list(int limit) {
		return db.sql("select * from agent_training order by created_at desc limit ?")
				.param(limit)
				.query(TrainingRepository::map)
				.list();
	}

	/** Trainings of one task, newest first — the set that gets compared. */
	public List<Training> listForTask(String taskId, int limit) {
		return db.sql("select * from agent_training where task_id = ? order by created_at desc limit ?")
				.params(taskId, limit)
				.query(TrainingRepository::map)
				.list();
	}

	/** Ids of the runs of a training in execution order. */
	public List<UUID> runIds(UUID trainingId) {
		return db.sql("select id from agent_run where training_id = ? order by started_at")
				.param(trainingId)
				.query(UUID.class)
				.list();
	}

	/** Executed tool calls per run of the training — the cost axis the run row does not carry. */
	public Map<UUID, Integer> toolCallsByRun(UUID trainingId) {
		Map<UUID, Integer> counts = new HashMap<>();
		db.sql("""
				select r.id, count(s.id) as calls
				  from agent_run r
				  left join agent_run_step s on s.run_id = r.id and s.type = ?
				 where r.training_id = ?
				 group by r.id
				""")
				.params(StepType.TOOL.name(), trainingId)
				.query((rs, rowNum) -> counts.put(rs.getObject("id", UUID.class), rs.getInt("calls")))
				.list();
		return counts;
	}

	// --- memory -----------------------------------------------------------------

	public void remember(UUID trainingId, String strategyId, UUID runId, String kind, String content) {
		db.sql("""
				insert into training_memory (training_id, strategy_id, run_id, kind, content, created_at)
				values (?, ?, ?, ?, ?, ?)
				""")
				.params(trainingId, strategyId, runId, kind, content, Timestamp.from(Instant.now()))
				.update();
	}

	/** Everything a training remembers, oldest first, across all strategies. */
	public List<TrainingMemory> memory(UUID trainingId) {
		return db.sql("select * from training_memory where training_id = ? order by id")
				.param(trainingId)
				.query(TrainingRepository::mapMemory)
				.list();
	}

	public List<TrainingMemory> memory(UUID trainingId, String strategyId) {
		return db.sql("select * from training_memory where training_id = ? and strategy_id = ? order by id")
				.params(trainingId, strategyId)
				.query(TrainingRepository::mapMemory)
				.list();
	}

	public List<TrainingMemory> memory(UUID trainingId, String strategyId, String kind) {
		return db.sql("select * from training_memory where training_id = ? and strategy_id = ? and kind = ? order by id")
				.params(trainingId, strategyId, kind)
				.query(TrainingRepository::mapMemory)
				.list();
	}

	private static Training map(ResultSet rs, int rowNum) throws SQLException {
		Timestamp finished = rs.getTimestamp("finished_at");
		return new Training(
				rs.getObject("id", UUID.class),
				rs.getString("task_id"),
				rs.getInt("task_version"),
				rs.getString("soul_id"),
				rs.getString("profile_id"),
				rs.getString("provider_id"),
				rs.getInt("repetitions"),
				TrainingStatus.valueOf(rs.getString("status")),
				rs.getInt("completed"),
				rs.getString("error"),
				rs.getTimestamp("created_at").toInstant(),
				finished == null ? null : finished.toInstant());
	}

	private static TrainingMemory mapMemory(ResultSet rs, int rowNum) throws SQLException {
		return new TrainingMemory(
				rs.getLong("id"),
				rs.getObject("training_id", UUID.class),
				rs.getString("strategy_id"),
				rs.getObject("run_id", UUID.class),
				rs.getString("kind"),
				rs.getString("content"),
				rs.getTimestamp("created_at").toInstant());
	}

	/** Tests and housekeeping only. */
	public void deleteAll() {
		db.sql("delete from agent_training").update();
	}
}
