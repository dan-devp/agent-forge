package com.devp.agent.run;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Speicher fuer Laeufe und Traces.
 *
 * <p>Bewusst {@link JdbcClient} statt JPA: Schritte werden nur angehaengt und nie
 * geaendert, und Schritt 3 braucht spaeter pgvector-Abfragen, bei denen ein ORM
 * nicht hilft.
 */
@Repository
public class RunRepository {

	private final JdbcClient db;

	public RunRepository(JdbcClient db) {
		this.db = db;
	}

	/** Legt den Lauf an, sobald er beginnt — ein Absturz bleibt so als RUNNING sichtbar. */
	public void start(AgentRun run) {
		db.sql("""
				insert into agent_run (id, auftrag, soul_id, soul_version, provider_id, modell, status, gestartet_am)
				values (?, ?, ?, ?, ?, ?, ?, ?)
				""")
				.params(run.id(), run.goal(), run.soulId(), run.soulVersion(), run.providerId(), run.model(),
						run.status().name(), Timestamp.from(run.startedAt()))
				.update();
	}

	public void addStep(UUID runId, RunStep step) {
		db.sql("""
				insert into agent_run_step (run_id, nr, art, name, eingabe, ausgabe, tokens, dauer_ms, fehler, erstellt_am)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
				.params(runId, step.nr(), step.type().name(), step.name(), step.input(), step.output(),
						step.tokens(), step.durationMs(), step.error(), Timestamp.from(step.createdAt()))
				.update();
	}

	public void finish(AgentRun run) {
		db.sql("""
				update agent_run
				   set status = ?, antwort = ?, fehler = ?, modell = ?, schritte = ?,
				       tokens_gesamt = ?, dauer_ms = ?, beendet_am = ?
				 where id = ?
				""")
				.params(run.status().name(), run.answer(), run.error(), run.model(), run.steps(),
						run.totalTokens(), run.durationMs(), Timestamp.from(run.finishedAt()), run.id())
				.update();
	}

	/** Naechste freie Schrittnummer. Der Trace ist die einzige Quelle — kein Zaehler im Speicher. */
	public int nextStepNumber(UUID runId) {
		return db.sql("select coalesce(max(nr), 0) + 1 from agent_run_step where run_id = ?")
				.param(runId)
				.query(Integer.class)
				.single();
	}

	/** Bisher ausgefuehrte Tool-Aufrufe des Laufs — Grundlage der Budgetpruefung. */
	public int countToolSteps(UUID runId) {
		return db.sql("select count(*) from agent_run_step where run_id = ? and art = ?")
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
		return run.map(r -> new AgentRun(r.id(), r.goal(), r.soulId(), r.soulVersion(), r.providerId(), r.model(),
				r.status(), r.answer(), r.error(), r.steps(), r.totalTokens(), r.durationMs(), r.startedAt(),
				r.finishedAt(), trace(id)));
	}

	/** Uebersicht ohne Trace — die Liste soll nicht den halben Speicher laden. */
	public List<AgentRun> list(int limit) {
		return db.sql("select * from agent_run order by gestartet_am desc limit ?")
				.param(limit)
				.query(RunRepository::mapRun)
				.list();
	}

	public List<RunStep> trace(UUID runId) {
		return db.sql("select * from agent_run_step where run_id = ? order by nr")
				.param(runId)
				.query((rs, rowNum) -> new RunStep(
						rs.getInt("nr"),
						StepType.valueOf(rs.getString("art")),
						rs.getString("name"),
						rs.getString("eingabe"),
						rs.getString("ausgabe"),
						(Integer) rs.getObject("tokens"),
						(Long) rs.getObject("dauer_ms"),
						rs.getString("fehler"),
						rs.getTimestamp("erstellt_am").toInstant()))
				.list();
	}

	private static AgentRun mapRun(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		Timestamp beendet = rs.getTimestamp("beendet_am");
		return new AgentRun(
				rs.getObject("id", UUID.class),
				rs.getString("auftrag"),
				rs.getString("soul_id"),
				rs.getInt("soul_version"),
				rs.getString("provider_id"),
				rs.getString("modell"),
				RunStatus.valueOf(rs.getString("status")),
				rs.getString("antwort"),
				rs.getString("fehler"),
				rs.getInt("schritte"),
				(Integer) rs.getObject("tokens_gesamt"),
				(Long) rs.getObject("dauer_ms"),
				rs.getTimestamp("gestartet_am").toInstant(),
				beendet == null ? null : beendet.toInstant(),
				List.of());
	}

	/** Nur fuer Tests und Aufraeumarbeiten. */
	public void deleteAll() {
		db.sql("delete from agent_run").update();
	}

	static Instant now() {
		return Instant.now();
	}
}
