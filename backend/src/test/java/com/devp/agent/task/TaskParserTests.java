package com.devp.agent.task;

import com.devp.agent.task.TaskParser.TaskParseException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskParserTests {

	private static final String VOLLSTAENDIG = """
			---
			id: zeit
			name: Zeit ermitteln
			version: 4
			beschreibung: Testdefinition
			schwierigkeit: 3
			tags: [werkzeuge, eingebaut]
			soul: reviewer
			provider: local
			max-tool-calls: 5
			max-duration-ms: 30000
			---

			# Auftrag

			Wie spaet ist es in UTC?

			# Hintergrund

			Prueft, ob das Tool genutzt wird.
			""";

	@Test
	void liestFrontmatterUndAuftrag() {
		Task task = TaskParser.parse(VOLLSTAENDIG, "zeit.md");

		assertThat(task.id()).isEqualTo("zeit");
		assertThat(task.name()).isEqualTo("Zeit ermitteln");
		assertThat(task.version()).isEqualTo(4);
		assertThat(task.beschreibung()).isEqualTo("Testdefinition");
		assertThat(task.schwierigkeit()).isEqualTo(3);
		assertThat(task.tags()).containsExactly("werkzeuge", "eingebaut");
		assertThat(task.auftrag()).isEqualTo("Wie spaet ist es in UTC?");
		assertThat(task.quelle()).isEqualTo("zeit.md");
	}

	/** Vorgaben der Aufgabe, die ein Aufruf ueberschreiben darf. */
	@Test
	void liestVorgabenUndBudget() {
		Task task = TaskParser.parse(VOLLSTAENDIG, "zeit.md");

		assertThat(task.soul()).isEqualTo("reviewer");
		assertThat(task.provider()).isEqualTo("local");
		assertThat(task.maxToolCalls()).isEqualTo(5);
		assertThat(task.maxDurationMs()).isEqualTo(30_000L);
	}

	@Test
	void behaeltWeitereAbschnitte() {
		Task task = TaskParser.parse(VOLLSTAENDIG, "zeit.md");

		assertThat(task.abschnitte().keySet()).containsExactly("Auftrag", "Hintergrund");
		assertThat(task.abschnitte().get("Hintergrund")).contains("Prueft");
	}

	@Test
	void nutztVorgabewerteWennNichtsAngegeben() {
		Task task = TaskParser.parse("""
				---
				id: minimal
				---
				# Auftrag
				Tu etwas.
				""", "minimal.md");

		assertThat(task.name()).isEqualTo("minimal");
		assertThat(task.version()).isEqualTo(1);
		assertThat(task.schwierigkeit()).isEqualTo(1);
		assertThat(task.tags()).isEmpty();
		assertThat(task.soul()).isNull();
		assertThat(task.maxToolCalls()).isNull();
		assertThat(task.maxDurationMs()).isNull();
	}

	@Test
	void faelltOhneIdAufDenDateinamenZurueck() {
		Task task = TaskParser.parse("""
				---
				name: Ohne Id
				---
				# Auftrag
				Tu etwas.
				""", "tasks/wichtig.md");

		assertThat(task.id()).isEqualTo("wichtig");
	}

	/** Ohne Auftrag gibt es nichts auszufuehren — das muss beim Laden auffallen, nicht im Lauf. */
	@Test
	void meldetFehlendenAuftrag() {
		assertThatThrownBy(() -> TaskParser.parse("""
				---
				id: leer
				---
				# Hintergrund
				Nur Prosa.
				""", "leer.md"))
				.isInstanceOf(TaskParseException.class)
				.hasMessageContaining("leer.md")
				.hasMessageContaining("Auftrag");
	}

	@Test
	void meldetLeerenAuftrag() {
		assertThatThrownBy(() -> TaskParser.parse("---\nid: leer\n---\n# Auftrag\n\n", "leer.md"))
				.isInstanceOf(TaskParseException.class)
				.hasMessageContaining("Auftrag");
	}

	@Test
	void meldetFehlendesFrontmatter() {
		assertThatThrownBy(() -> TaskParser.parse("# Auftrag\nTu etwas.", "kaputt.md"))
				.isInstanceOf(TaskParseException.class)
				.hasMessageContaining("Frontmatter");
	}

	@Test
	void meldetNichtNumerischeSchwierigkeit() {
		assertThatThrownBy(() -> TaskParser.parse("""
				---
				id: x
				schwierigkeit: schwer
				---
				# Auftrag
				Tu etwas.
				""", "x.md"))
				.isInstanceOf(TaskParseException.class)
				.hasMessageContaining("schwierigkeit");
	}

	@Test
	void akzeptiertEinzelnesTagOhneListe() {
		Task task = TaskParser.parse("""
				---
				id: x
				tags: werkzeuge
				---
				# Auftrag
				Tu etwas.
				""", "x.md");

		assertThat(task.tags()).containsExactly("werkzeuge");
	}
}
