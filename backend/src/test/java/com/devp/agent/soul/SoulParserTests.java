package com.devp.agent.soul;

import java.util.List;

import com.devp.agent.soul.SoulParser.SoulParseException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SoulParserTests {

	private static final String VOLLSTAENDIG = """
			---
			id: pruefer
			name: Argus
			rolle: Reviewer
			version: 3
			beschreibung: Testdefinition
			gates:
			  - id: beleg
			    frage: Ist das belegt?
			    blockiert: true
			  - id: umfang
			    frage: Ist der Umfang richtig?
			---

			# Identitaet

			Du bist Argus.

			# Werte

			- Belegt schlaegt plausibel.
			""";

	@Test
	void liestFrontmatterUndAbschnitte() {
		Soul soul = SoulParser.parse(VOLLSTAENDIG, "pruefer.md");

		assertThat(soul.id()).isEqualTo("pruefer");
		assertThat(soul.name()).isEqualTo("Argus");
		assertThat(soul.rolle()).isEqualTo("Reviewer");
		assertThat(soul.version()).isEqualTo(3);
		assertThat(soul.beschreibung()).isEqualTo("Testdefinition");
		assertThat(soul.quelle()).isEqualTo("pruefer.md");
		assertThat(soul.abschnitte()).containsOnlyKeys("Identitaet", "Werte");
		assertThat(soul.abschnitte().get("Identitaet")).isEqualTo("Du bist Argus.");
	}

	/** Die Reihenfolge der Abschnitte ist Teil der SOUL — sie bestimmt den Prompt-Aufbau. */
	@Test
	void behaeltAbschnittsreihenfolge() {
		Soul soul = SoulParser.parse(VOLLSTAENDIG, "pruefer.md");

		assertThat(soul.abschnitte().keySet()).containsExactly("Identitaet", "Werte");
	}

	@Test
	void liestGatesInklBlockierFlag() {
		Soul soul = SoulParser.parse(VOLLSTAENDIG, "pruefer.md");

		assertThat(soul.gates()).containsExactly(
				new SoulGate("beleg", "Ist das belegt?", true),
				new SoulGate("umfang", "Ist der Umfang richtig?", false));
	}

	@Test
	void faelltOhneIdAufDenDateinamenZurueck() {
		Soul soul = SoulParser.parse("""
				---
				name: Ohne Id
				---
				# Identitaet
				Text.
				""", "souls/forge.md");

		assertThat(soul.id()).isEqualTo("forge");
		assertThat(soul.version()).isEqualTo(1);
		assertThat(soul.gates()).isEmpty();
	}

	@Test
	void akzeptiertWindowsZeilenenden() {
		Soul soul = SoulParser.parse("---\r\nid: x\r\n---\r\n# Identitaet\r\nText.\r\n", "x.md");

		assertThat(soul.abschnitte()).containsEntry("Identitaet", "Text.");
	}

	@Test
	void meldetFehlendesFrontmatter() {
		assertThatThrownBy(() -> SoulParser.parse("# Identitaet\nText.", "kaputt.md"))
				.isInstanceOf(SoulParseException.class)
				.hasMessageContaining("kaputt.md")
				.hasMessageContaining("Frontmatter");
	}

	@Test
	void meldetNichtGeschlossenesFrontmatter() {
		assertThatThrownBy(() -> SoulParser.parse("---\nid: x\n# Identitaet\n", "offen.md"))
				.isInstanceOf(SoulParseException.class)
				.hasMessageContaining("nicht geschlossen");
	}

	@Test
	void meldetGateOhnePflichtfelder() {
		assertThatThrownBy(() -> SoulParser.parse("""
				---
				id: x
				gates:
				  - id: ohne-frage
				---
				""", "gate.md"))
				.isInstanceOf(SoulParseException.class)
				.hasMessageContaining("Gate ohne");
	}

	@Test
	void meldetNichtNumerischeVersion() {
		assertThatThrownBy(() -> SoulParser.parse("---\nid: x\nversion: zwei\n---\n", "v.md"))
				.isInstanceOf(SoulParseException.class)
				.hasMessageContaining("version");
	}

	@Test
	void meldetLeereDatei() {
		assertThatThrownBy(() -> SoulParser.parse("", "leer.md"))
				.isInstanceOf(SoulParseException.class);
	}

	/** Abschnitte ohne Inhalt tragen nichts zum Prompt bei und werden verworfen. */
	@Test
	void verwirftLeereAbschnitte() {
		Soul soul = SoulParser.parse("""
				---
				id: x
				---
				# Leer

				# Gefuellt
				Inhalt.
				""", "x.md");

		assertThat(soul.abschnitte()).containsOnlyKeys("Gefuellt");
		assertThat(soul.gates()).isEqualTo(List.of());
	}
}
