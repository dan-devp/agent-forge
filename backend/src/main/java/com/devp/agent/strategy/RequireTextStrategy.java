package com.devp.agent.strategy;

import java.util.Locale;
import java.util.Optional;

import com.devp.agent.profile.Profile.StrategyConfig;

import org.springframework.stereotype.Component;

/**
 * Fixture for the {@code beforeFinish} loop, not a learning strategy: rejects any answer that
 * lacks the configured text and sends the model back once more.
 *
 * <p>Exists so the rejection mechanics can be exercised against the mock provider, whose
 * fixed answer never contains the text. Parameters: {@code text} (required),
 * {@code max-rounds} (default 1).
 */
@Component
public class RequireTextStrategy implements Strategy {

	public static final String ID = "require-text";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Optional<String> beforeFinish(RunContext context, StrategyConfig config, String answer) {
		String text = config.param("text");
		if (text == null || text.isBlank()) {
			return Optional.empty();
		}
		boolean present = answer != null && answer.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
		return present
				? Optional.empty()
				// German on purpose: this goes to the model.
				: Optional.of("Deine Antwort muss den Text '%s' enthalten. Ueberarbeite sie.".formatted(text));
	}
}
