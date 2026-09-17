package com.devp.agent.soul;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SoulRendererTests {

	private final SoulRenderer renderer = new SoulRenderer();

	private static Soul soul(List<SoulGate> gates) {
		Map<String, String> sections = new LinkedHashMap<>();
		sections.put("Identitaet", "Du pruefst fremde Arbeit.");
		sections.put("Ton", "Knapp.");
		return new Soul("checker", "Argus", "Reviewer", 3, "test definition", gates, sections, "checker.md");
	}

	@Test
	void namesNameAndRole() {
		String prompt = renderer.render(soul(List.of()));

		assertThat(prompt).startsWith("# Wer du bist\nDu bist Argus (Reviewer).");
	}

	@Test
	void rendersSectionsInFileOrder() {
		String prompt = renderer.render(soul(List.of()));

		assertThat(prompt.indexOf("# Identitaet")).isLessThan(prompt.indexOf("# Ton"));
		assertThat(prompt).contains("Du pruefst fremde Arbeit.").contains("Knapp.");
	}

	@Test
	void rendersGatesAsMandatoryQuestions() {
		String prompt = renderer.render(soul(List.of(new SoulGate("evidence", "Ist das belegt?", false))));

		assertThat(prompt).contains("# Pflichtfragen").contains("- Ist das belegt?");
	}

	/** A blocking gate must be recognizable as blocking in the prompt. */
	@Test
	void marksBlockingGates() {
		String prompt = renderer.render(soul(List.of(
				new SoulGate("evidence", "Ist das belegt?", true),
				new SoulGate("scope", "Umfang richtig?", false))));

		assertThat(prompt).contains("Ist das belegt? Faellt die Antwort negativ aus, lieferst du nicht");
		assertThat(prompt).endsWith("- Umfang richtig?");
		assertThat(prompt.split("Faellt die Antwort negativ aus")).hasSize(2);
	}

	@Test
	void omitsMandatoryQuestionsWithoutGates() {
		assertThat(renderer.render(soul(List.of()))).doesNotContain("Pflichtfragen");
	}

	@Test
	void usesOnlyTheNameWithoutRole() {
		Soul withoutRole = new Soul("x", "Argus", null, 1, null, List.of(), Map.of(), "x.md");

		assertThat(renderer.render(withoutRole)).contains("Du bist Argus.");
	}
}
