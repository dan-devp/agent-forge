package com.devp.forge;

import com.devp.forge.ai.ChatClientRegistry;
import com.devp.forge.config.ModelProperties;
import com.devp.forge.model.Model;
import com.devp.forge.model.ModelRepository;
import com.devp.forge.model.Provider;
import com.devp.forge.support.PostgresTestcontainer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards that no automated test can call a real model.
 *
 * <p>Real model calls cost money and quota and are made exclusively by the user through the
 * UI. The protection is {@code app.models.base-url-override} in
 * {@code application-test.properties}; this test makes sure it is set and applied to every
 * model, whatever its file says.
 *
 * <p>If it fails, fix the cause, do not adjust this test.
 */
@SpringBootTest
@Import(PostgresTestcontainer.class)
class NoRealModelCallsTests {

	private static final String DEAD = "http://127.0.0.1:1/v1";

	@Autowired
	private ModelProperties properties;

	@Autowired
	private ModelRepository models;

	@Autowired
	private ChatClientRegistry registry;

	@Test
	void overrideIsSet() {
		assertThat(properties.getBaseUrlOverride()).isEqualTo(DEAD);
	}

	@Test
	void everyModelIsRedirected() {
		assertThat(models.list()).isNotEmpty();
		for (Model model : models.list()) {
			assertThat(registry.effectiveBaseUrl(model))
					.as("model '%s' must point to a dead address in tests", model.id())
					.isEqualTo(DEAD);
		}
	}

	@Test
	void aModelSavedWithARealUrlIsStillRedirected() {
		Model real = new Model("guard-" + System.nanoTime(), 0, "Guard", null, Provider.LOCAL,
				"https://api.openai.com/v1", "gpt-4o-mini", null, 0.7);
		Model saved = models.create(real);
		try {
			assertThat(registry.effectiveBaseUrl(saved)).isEqualTo(DEAD);
		}
		finally {
			models.archive(saved.id());
		}
	}

	@Test
	void aDecisionModelIsRedirectedToo() {
		Model jev = new Model("guard-jev-" + System.nanoTime(), 0, "Jev", null, Provider.TYPESAFE,
				Provider.TYPESAFE.fixedBaseUrl(), "jev-latest", "API_KEY_TYPESAFE", 0.7);
		Model saved = models.create(jev);
		try {
			// The decision registry takes the same effective URL and only cuts the version path.
			assertThat(registry.effectiveBaseUrl(saved)).isEqualTo(DEAD);
		}
		finally {
			models.archive(saved.id());
		}
	}
}
