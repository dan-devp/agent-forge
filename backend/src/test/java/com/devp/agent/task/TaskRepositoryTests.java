package com.devp.agent.task;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.devp.agent.config.TaskProperties;
import com.devp.agent.task.TaskRepository.UnknownTaskException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskRepositoryTests {

	private static TaskRepository repository(String... locations) {
		TaskProperties properties = new TaskProperties();
		properties.setLocations(List.of(locations));
		return new TaskRepository(properties);
	}

	@Test
	void laedtMitgelieferteAufgaben() {
		TaskRepository repository = repository("classpath:tasks/");

		assertThat(repository.list()).extracting(Task::id)
				.contains("zeit-und-rechnung", "mcp-recherche", "kein-werkzeug");
		assertThat(repository.resolve("zeit-und-rechnung").auftrag()).contains("100 geteilt durch 7");
	}

	@Test
	void meldetUnbekannteAufgabe() {
		TaskRepository repository = repository("classpath:tasks/");

		assertThatThrownBy(() -> repository.resolve("gibtsnicht"))
				.isInstanceOf(UnknownTaskException.class)
				.hasMessageContaining("zeit-und-rechnung");
	}

	@Test
	void ohneDefinitionIstDieListeLeer() {
		assertThat(repository("classpath:gibtsnicht/").list()).isEmpty();
	}

	/** Spaetere Location gewinnt — so laesst sich ein Aufgabensatz austauschen. */
	@Test
	void externeLocationUeberschreibtGleicheId(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("zeit-und-rechnung.md"), """
				---
				id: zeit-und-rechnung
				name: Ersetzt
				version: 9
				---
				# Auftrag
				Anderer Auftrag.
				""");

		TaskRepository repository = repository("classpath:tasks/", dir.toUri().toString());

		Task ersetzt = repository.resolve("zeit-und-rechnung");
		assertThat(ersetzt.name()).isEqualTo("Ersetzt");
		assertThat(ersetzt.version()).isEqualTo(9);
		assertThat(ersetzt.auftrag()).isEqualTo("Anderer Auftrag.");
		// Die nicht ueberschriebene Aufgabe bleibt erhalten.
		assertThat(repository.resolve("mcp-recherche").name()).isEqualTo("Recherche ueber MCP");
	}

	@Test
	void reloadLiestNeueDateienEin(@TempDir Path dir) throws Exception {
		TaskRepository repository = repository(dir.toUri().toString());
		assertThat(repository.list()).isEmpty();

		Files.writeString(dir.resolve("neu.md"), """
				---
				id: neu
				name: Neu
				---
				# Auftrag
				Frisch geladen.
				""");

		assertThat(repository.reload()).isEqualTo(1);
		assertThat(repository.resolve("neu").auftrag()).isEqualTo("Frisch geladen.");
	}

	/** Die mitgelieferten Aufgaben muessen ladbar sein — sonst faellt es erst im Betrieb auf. */
	@Test
	void mitgelieferteAufgabenSindVollstaendig() {
		repository("classpath:tasks/").list().forEach(task -> {
			assertThat(task.id()).isNotBlank();
			assertThat(task.auftrag()).isNotBlank();
			assertThat(task.version()).isPositive();
		});
	}
}
