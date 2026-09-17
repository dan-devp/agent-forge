package com.devp.agent;

import com.devp.agent.config.AiProperties;
import com.devp.agent.support.PostgresTestcontainer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wacht darueber, dass kein automatischer Test ein echtes Modell aufrufen kann.
 *
 * <p>Echte Modellaufrufe kosten Geld und Kontingent und macht ausschliesslich der Nutzer
 * ueber die UI. Der Schutz liegt in {@code src/test/resources/application-test.properties};
 * dieser Test stellt sicher, dass er wirkt — auch dann, wenn im Terminal
 * {@code LOCAL_AI_BASE_URL} oder {@code OPENAI_API_KEY} gesetzt sind.
 *
 * <p>Schlaegt er fehl, zeigt ein Provider auf einen echten Endpunkt. Dann nicht diesen
 * Test anpassen, sondern die Ursache beseitigen.
 */
@SpringBootTest
@Import(PostgresTestcontainer.class)
class NoRealModelCallsTests {

	@Autowired
	private AiProperties ai;

	@Test
	void keinProviderZeigtAufEinenEchtenEndpunkt() {
		assertThat(ai.getProviders()).isNotEmpty();

		ai.getProviders().forEach((id, provider) -> assertThat(provider.getBaseUrl())
				.as("Provider '%s' muss im Test auf eine tote Adresse zeigen", id)
				.isEqualTo("http://127.0.0.1:1/v1"));
	}

	@Test
	void keinProviderTraegtEinenEchtenSchluessel() {
		ai.getProviders().forEach((id, provider) -> assertThat(provider.getApiKey())
				.as("Provider '%s' darf im Test keinen echten API-Key tragen", id)
				.isIn("not-set", "not-needed"));
	}
}
