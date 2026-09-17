package com.devp.agent.series;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.devp.agent.run.StepType;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Storage for series. The runs themselves live in {@code agent_run}, linked by {@code series_id}. */
@Repository
public class SeriesRepository {

	private final JdbcClient db;

	public SeriesRepository(JdbcClient db) {
		this.db = db;
	}

	public void create(Series series) {
		db.sql("""
				insert into agent_series (id, task_id, task_version, soul_id, profile_id, provider_id, repetitions,
				                          status, completed, created_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
				.params(series.id(), series.taskId(), series.taskVersion(), series.soulId(), series.profileId(),
						series.providerId(), series.repetitions(), series.status().name(), series.completed(),
						Timestamp.from(series.createdAt()))
				.update();
	}

	public void progress(UUID id, int completed) {
		db.sql("update agent_series set completed = ? where id = ?").params(completed, id).update();
	}

	public void finish(Series series) {
		db.sql("update agent_series set status = ?, error = ?, completed = ?, finished_at = ? where id = ?")
				.params(series.status().name(), series.error(), series.completed(),
						Timestamp.from(series.finishedAt()), series.id())
				.update();
	}

	public Optional<Series> find(UUID id) {
		return db.sql("select * from agent_series where id = ?").param(id).query(SeriesRepository::map).optional();
	}

	public List<Series> list(int limit) {
		return db.sql("select * from agent_series order by created_at desc limit ?")
				.param(limit)
				.query(SeriesRepository::map)
				.list();
	}

	/** Ids of the runs of a series in execution order. */
	public List<UUID> runIds(UUID seriesId) {
		return db.sql("select id from agent_run where series_id = ? order by started_at")
				.param(seriesId)
				.query(UUID.class)
				.list();
	}

	/** Executed tool calls per run of the series — the cost axis the run row does not carry. */
	public Map<UUID, Integer> toolCallsByRun(UUID seriesId) {
		Map<UUID, Integer> counts = new HashMap<>();
		db.sql("""
				select r.id, count(s.id) as calls
				  from agent_run r
				  left join agent_run_step s on s.run_id = r.id and s.type = ?
				 where r.series_id = ?
				 group by r.id
				""")
				.params(StepType.TOOL.name(), seriesId)
				.query((rs, rowNum) -> counts.put(rs.getObject("id", UUID.class), rs.getInt("calls")))
				.list();
		return counts;
	}

	private static Series map(ResultSet rs, int rowNum) throws SQLException {
		Timestamp finished = rs.getTimestamp("finished_at");
		return new Series(
				rs.getObject("id", UUID.class),
				rs.getString("task_id"),
				rs.getInt("task_version"),
				rs.getString("soul_id"),
				rs.getString("profile_id"),
				rs.getString("provider_id"),
				rs.getInt("repetitions"),
				SeriesStatus.valueOf(rs.getString("status")),
				rs.getInt("completed"),
				rs.getString("error"),
				rs.getTimestamp("created_at").toInstant(),
				finished == null ? null : finished.toInstant());
	}

	/** Tests and housekeeping only. */
	public void deleteAll() {
		db.sql("delete from agent_series").update();
	}
}
