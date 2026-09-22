package com.devp.agent.plan;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Storage for plans. The executions live in {@code agent_experiment}, linked by {@code plan_id}. */
@Repository
public class PlanRepository {

	private final JdbcClient db;

	public PlanRepository(JdbcClient db) {
		this.db = db;
	}

	public void create(Plan plan) {
		db.sql("""
				insert into experiment_plan (id, name, description, task_id, agent_id, provider_id, profiles,
				                             include_baseline, repetitions, created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
				.params(plan.id(), plan.name(), plan.description(), plan.taskId(), plan.agentId(), plan.providerId(),
						plan.profiles().toArray(String[]::new), plan.includeBaseline(), plan.repetitions(),
						Timestamp.from(plan.createdAt()), Timestamp.from(plan.updatedAt()))
				.update();
	}

	/** Editing a plan does not touch its executions — they keep what applied when they ran. */
	public void update(Plan plan) {
		db.sql("""
				update experiment_plan
				   set name = ?, description = ?, task_id = ?, agent_id = ?, provider_id = ?, profiles = ?,
				       include_baseline = ?, repetitions = ?, updated_at = ?
				 where id = ?
				""")
				.params(plan.name(), plan.description(), plan.taskId(), plan.agentId(), plan.providerId(),
						plan.profiles().toArray(String[]::new), plan.includeBaseline(), plan.repetitions(),
						Timestamp.from(plan.updatedAt()), plan.id())
				.update();
	}

	public Optional<Plan> find(UUID id) {
		return db.sql("select * from experiment_plan where id = ?")
				.param(id)
				.query(PlanRepository::map)
				.optional();
	}

	public List<Plan> list() {
		return db.sql("select * from experiment_plan order by created_at desc").query(PlanRepository::map).list();
	}

	/** Deleting a plan keeps its experiments: a measurement stays valid without its recipe. */
	public void delete(UUID id) {
		db.sql("delete from experiment_plan where id = ?").param(id).update();
	}

	private static Plan map(ResultSet rs, int rowNum) throws SQLException {
		return new Plan(
				rs.getObject("id", UUID.class),
				rs.getString("name"),
				rs.getString("description"),
				rs.getString("task_id"),
				rs.getString("agent_id"),
				rs.getString("provider_id"),
				strings(rs.getArray("profiles")),
				rs.getBoolean("include_baseline"),
				rs.getInt("repetitions"),
				rs.getTimestamp("created_at").toInstant(),
				rs.getTimestamp("updated_at").toInstant());
	}

	private static List<String> strings(Array array) throws SQLException {
		return array == null ? List.of() : List.of((String[]) array.getArray());
	}

	/** Tests and housekeeping only. */
	public void deleteAll() {
		db.sql("delete from experiment_plan").update();
	}
}
