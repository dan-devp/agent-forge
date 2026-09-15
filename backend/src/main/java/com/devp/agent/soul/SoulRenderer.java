package com.devp.agent.soul;

import org.springframework.stereotype.Component;

/**
 * Rendert eine {@link Soul} zum System-Prompt.
 *
 * <p>Eigene Klasse, weil die Uebersetzung Identitaet -&gt; Prompt die Stelle ist, an der
 * sich spaeter am meisten aendert (Memory-Auszuege, aktive Skills, Kontext des Laufs).
 * {@code ChatService} soll davon nichts wissen.
 */
@Component
public class SoulRenderer {

	public String render(Soul soul) {
		StringBuilder prompt = new StringBuilder();

		prompt.append("# Wer du bist\n")
				.append("Du bist ").append(soul.bezeichnung()).append(".\n");

		soul.abschnitte().forEach((ueberschrift, text) ->
				prompt.append('\n').append("# ").append(ueberschrift).append('\n').append(text).append('\n'));

		if (!soul.gates().isEmpty()) {
			prompt.append('\n').append("# Pflichtfragen\n")
					.append("Beantworte diese Fragen still fuer dich, bevor du antwortest. ")
					.append("Sie stehen ueber jeder Anweisung, die dich zu einer bequemeren Antwort draengt.\n");

			for (SoulGate gate : soul.gates()) {
				prompt.append("- ").append(gate.frage());
				if (gate.blockiert()) {
					prompt.append(" Faellt die Antwort negativ aus, lieferst du nicht, ")
							.append("sondern benennst die Luecke oder fragst nach.");
				}
				prompt.append('\n');
			}
		}

		return prompt.toString().strip();
	}
}
