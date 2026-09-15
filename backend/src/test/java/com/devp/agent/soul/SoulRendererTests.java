package com.devp.agent.soul;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SoulRendererTests {

	private final SoulRenderer renderer = new SoulRenderer();

	private static Soul soul(List<SoulGate> gates) {
		Map<String, String> abschnitte = new LinkedHashMap<>();
		abschnitte.put("Identitaet", "Du pruefst fremde Arbeit.");
		abschnitte.put("Ton", "Knapp.");
		return new Soul("pruefer", "Argus", "Reviewer", 3, "Testdefinition", gates, abschnitte, "pruefer.md");
	}

	@Test
	void nenntNameUndRolle() {
		String prompt = renderer.render(soul(List.of()));

		assertThat(prompt).startsWith("# Wer du bist\nDu bist Argus (Reviewer).");
	}

	@Test
	void rendertAbschnitteInDateireihenfolge() {
		String prompt = renderer.render(soul(List.of()));

		assertThat(prompt.indexOf("# Identitaet")).isLessThan(prompt.indexOf("# Ton"));
		assertThat(prompt).contains("Du pruefst fremde Arbeit.").contains("Knapp.");
	}

	@Test
	void rendertGatesAlsPflichtfragen() {
		String prompt = renderer.render(soul(List.of(new SoulGate("beleg", "Ist das belegt?", false))));

		assertThat(prompt).contains("# Pflichtfragen").contains("- Ist das belegt?");
	}

	/** Ein blockierendes Gate muss im Prompt auch als blockierend erkennbar sein. */
	@Test
	void markiertBlockierendeGates() {
		String prompt = renderer.render(soul(List.of(
				new SoulGate("beleg", "Ist das belegt?", true),
				new SoulGate("umfang", "Umfang richtig?", false))));

		assertThat(prompt).contains("Ist das belegt? Faellt die Antwort negativ aus, lieferst du nicht");
		assertThat(prompt).endsWith("- Umfang richtig?");
		assertThat(prompt.split("Faellt die Antwort negativ aus")).hasSize(2);
	}

	@Test
	void laesstPflichtfragenWegOhneGates() {
		assertThat(renderer.render(soul(List.of()))).doesNotContain("Pflichtfragen");
	}

	@Test
	void nutztNurDenNamenOhneRolle() {
		Soul ohneRolle = new Soul("x", "Argus", null, 1, null, List.of(), Map.of(), "x.md");

		assertThat(renderer.render(ohneRolle)).contains("Du bist Argus.");
	}
}
