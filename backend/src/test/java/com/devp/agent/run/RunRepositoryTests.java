package com.devp.agent.run;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.devp.agent.support.PostgresTestcontainer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestcontainer.class)
class RunRepositoryTests {

	@Autowired
	private RunRepository repository;

	@BeforeEach
	void leeren() {
		repository.deleteAll();
	}

	private AgentRun gestartet(UUID id) {
		return new AgentRun(id, "Wie spaet ist es?", "forge", 1, "local", "mock-local-7b",
				RunStatus.RUNNING, null, null, 0, null, null, Instant.now(), null, List.of());
	}

	@Test
	void schreibtUndLiestEinenLauf() {
		UUID id = UUID.randomUUID();
		repository.start(gestartet(id));

		AgentRun gelesen = repository.find(id).orElseThrow();
		assertThat(gelesen.goal()).isEqualTo("Wie spaet ist es?");
		assertThat(gelesen.soulId()).isEqualTo("forge");
		assertThat(gelesen.soulVersion()).isEqualTo(1);
		assertThat(gelesen.status()).isEqualTo(RunStatus.RUNNING);
		assertThat(gelesen.trace()).isEmpty();
	}

	@Test
	void haengtSchritteInReihenfolgeAn() {
		UUID id = UUID.randomUUID();
		repository.start(gestartet(id));

		repository.addStep(id, new RunStep(repository.nextStepNumber(id), StepType.MODEL, "mock-local-7b",
				null, "fordert Tools an: aktuelleZeit({})", 40, null, null, Instant.now()));
		repository.addStep(id, new RunStep(repository.nextStepNumber(id), StepType.TOOL, "aktuelleZeit",
				"{}", "2026-09-17 10:00:00", null, 12L, null, Instant.now()));

		List<RunStep> trace = repository.trace(id);
		assertThat(trace).extracting(RunStep::nr).containsExactly(1, 2);
		assertThat(trace).extracting(RunStep::type).containsExactly(StepType.MODEL, StepType.TOOL);
		assertThat(trace.get(1).output()).isEqualTo("2026-09-17 10:00:00");
		assertThat(trace.get(1).durationMs()).isEqualTo(12L);
	}

	/** nextStepNumber zaehlt aus dem Trace, nicht aus einem Zaehler im Speicher. */
	@Test
	void vergibtSchrittnummernFortlaufend() {
		UUID id = UUID.randomUUID();
		repository.start(gestartet(id));

		assertThat(repository.nextStepNumber(id)).isEqualTo(1);
		repository.addStep(id, new RunStep(1, StepType.MODEL, "m", null, "x", null, null, null, Instant.now()));
		assertThat(repository.nextStepNumber(id)).isEqualTo(2);
	}

	@Test
	void schreibtDenAbschlussZurueck() {
		UUID id = UUID.randomUUID();
		AgentRun start = gestartet(id);
		repository.start(start);

		Instant ende = Instant.now();
		repository.finish(new AgentRun(id, start.goal(), start.soulId(), start.soulVersion(), start.providerId(),
				"mock-local-7b", RunStatus.DONE, "Es ist 10 Uhr.", null, 2, 120, 850L, start.startedAt(), ende,
				List.of()));

		AgentRun fertig = repository.find(id).orElseThrow();
		assertThat(fertig.status()).isEqualTo(RunStatus.DONE);
		assertThat(fertig.answer()).isEqualTo("Es ist 10 Uhr.");
		assertThat(fertig.totalTokens()).isEqualTo(120);
		assertThat(fertig.durationMs()).isEqualTo(850L);
		assertThat(fertig.finishedAt()).isNotNull();
	}

	@Test
	void findLiefertDenTraceMit() {
		UUID id = UUID.randomUUID();
		repository.start(gestartet(id));
		repository.addStep(id, new RunStep(1, StepType.TOOL, "teilen", "{\"dividend\":100}", "14 Rest 2",
				null, 5L, null, Instant.now()));

		assertThat(repository.find(id).orElseThrow().trace()).hasSize(1);
	}

	/** Die Uebersicht laedt bewusst keinen Trace — sonst zieht sie den halben Speicher. */
	@Test
	void listLiefertNeuesteZuerstUndOhneTrace() {
		UUID alt = UUID.randomUUID();
		UUID neu = UUID.randomUUID();
		repository.start(new AgentRun(alt, "alt", "forge", 1, "local", null, RunStatus.RUNNING, null, null, 0,
				null, null, Instant.now().minusSeconds(60), null, List.of()));
		repository.start(new AgentRun(neu, "neu", "forge", 1, "local", null, RunStatus.RUNNING, null, null, 0,
				null, null, Instant.now(), null, List.of()));
		repository.addStep(neu, new RunStep(1, StepType.MODEL, "m", null, "x", null, null, null, Instant.now()));

		List<AgentRun> liste = repository.list(10);
		assertThat(liste).extracting(AgentRun::goal).containsExactly("neu", "alt");
		assertThat(liste.getFirst().trace()).isEmpty();
	}

	@Test
	void unbekannterLaufIstLeer() {
		assertThat(repository.find(UUID.randomUUID())).isEmpty();
	}
}
