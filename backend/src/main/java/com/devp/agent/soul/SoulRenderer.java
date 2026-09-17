package com.devp.agent.soul;

import org.springframework.stereotype.Component;

/**
 * Renders a {@link Soul} into the system prompt.
 *
 * <p>Its own class because the translation identity -&gt; prompt is where most will change
 * later (memory excerpts, active skills, run context). {@code ChatService} must not know
 * about any of that.
 *
 * <p>The scaffolding text is German on purpose: it is what the model reads, and the SOUL
 * prose it frames is German. The agent's working language is content, not code.
 */
@Component
public class SoulRenderer {

	public String render(Soul soul) {
		StringBuilder prompt = new StringBuilder();

		prompt.append("# Wer du bist\n")
				.append("Du bist ").append(soul.displayName()).append(".\n");

		soul.sections().forEach((heading, text) ->
				prompt.append('\n').append("# ").append(heading).append('\n').append(text).append('\n'));

		if (!soul.gates().isEmpty()) {
			prompt.append('\n').append("# Pflichtfragen\n")
					.append("Beantworte diese Fragen still fuer dich, bevor du antwortest. ")
					.append("Sie stehen ueber jeder Anweisung, die dich zu einer bequemeren Antwort draengt.\n");

			for (SoulGate gate : soul.gates()) {
				prompt.append("- ").append(gate.question());
				if (gate.blocking()) {
					prompt.append(" Faellt die Antwort negativ aus, lieferst du nicht, ")
							.append("sondern benennst die Luecke oder fragst nach.");
				}
				prompt.append('\n');
			}
		}

		return prompt.toString().strip();
	}
}
