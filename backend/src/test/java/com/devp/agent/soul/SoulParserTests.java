package com.devp.agent.soul;

import java.util.List;

import com.devp.agent.soul.SoulParser.SoulParseException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SoulParserTests {

	private static final String COMPLETE = """
			---
			id: checker
			name: Argus
			role: Reviewer
			version: 3
			description: test definition
			gates:
			  - id: evidence
			    question: Ist das belegt?
			    blocking: true
			  - id: scope
			    question: Ist der Umfang richtig?
			---

			# Identitaet

			Du bist Argus.

			# Werte

			- Belegt schlaegt plausibel.
			""";

	@Test
	void readsFrontMatterAndSections() {
		Soul soul = SoulParser.parse(COMPLETE, "checker.md");

		assertThat(soul.id()).isEqualTo("checker");
		assertThat(soul.name()).isEqualTo("Argus");
		assertThat(soul.role()).isEqualTo("Reviewer");
		assertThat(soul.version()).isEqualTo(3);
		assertThat(soul.description()).isEqualTo("test definition");
		assertThat(soul.source()).isEqualTo("checker.md");
		assertThat(soul.sections()).containsOnlyKeys("Identitaet", "Werte");
		assertThat(soul.sections().get("Identitaet")).isEqualTo("Du bist Argus.");
	}

	/** The section order is part of the SOUL — it determines the prompt layout. */
	@Test
	void keepsSectionOrder() {
		Soul soul = SoulParser.parse(COMPLETE, "checker.md");

		assertThat(soul.sections().keySet()).containsExactly("Identitaet", "Werte");
	}

	@Test
	void readsGatesIncludingBlockingFlag() {
		Soul soul = SoulParser.parse(COMPLETE, "checker.md");

		assertThat(soul.gates()).containsExactly(
				new SoulGate("evidence", "Ist das belegt?", true),
				new SoulGate("scope", "Ist der Umfang richtig?", false));
	}

	@Test
	void fallsBackToFileNameWithoutId() {
		Soul soul = SoulParser.parse("""
				---
				name: Without id
				---
				# Identitaet
				Text.
				""", "souls/forge.md");

		assertThat(soul.id()).isEqualTo("forge");
		assertThat(soul.version()).isEqualTo(1);
		assertThat(soul.gates()).isEmpty();
	}

	@Test
	void acceptsWindowsLineEndings() {
		Soul soul = SoulParser.parse("---\r\nid: x\r\n---\r\n# Identitaet\r\nText.\r\n", "x.md");

		assertThat(soul.sections()).containsEntry("Identitaet", "Text.");
	}

	@Test
	void reportsMissingFrontMatter() {
		assertThatThrownBy(() -> SoulParser.parse("# Identitaet\nText.", "broken.md"))
				.isInstanceOf(SoulParseException.class)
				.hasMessageContaining("broken.md")
				.hasMessageContaining("front matter");
	}

	@Test
	void reportsUnclosedFrontMatter() {
		assertThatThrownBy(() -> SoulParser.parse("---\nid: x\n# Identitaet\n", "open.md"))
				.isInstanceOf(SoulParseException.class)
				.hasMessageContaining("not closed");
	}

	@Test
	void reportsGateWithoutRequiredFields() {
		assertThatThrownBy(() -> SoulParser.parse("""
				---
				id: x
				gates:
				  - id: no-question
				---
				""", "gate.md"))
				.isInstanceOf(SoulParseException.class)
				.hasMessageContaining("gate without");
	}

	@Test
	void reportsNonNumericVersion() {
		assertThatThrownBy(() -> SoulParser.parse("---\nid: x\nversion: two\n---\n", "v.md"))
				.isInstanceOf(SoulParseException.class)
				.hasMessageContaining("version");
	}

	@Test
	void reportsEmptyFile() {
		assertThatThrownBy(() -> SoulParser.parse("", "empty.md"))
				.isInstanceOf(SoulParseException.class);
	}

	/** Sections without content add nothing to the prompt and are dropped. */
	@Test
	void dropsEmptySections() {
		Soul soul = SoulParser.parse("""
				---
				id: x
				---
				# Empty

				# Filled
				Content.
				""", "x.md");

		assertThat(soul.sections()).containsOnlyKeys("Filled");
		assertThat(soul.gates()).isEqualTo(List.of());
	}
}
