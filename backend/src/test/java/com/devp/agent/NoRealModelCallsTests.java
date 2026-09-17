package com.devp.agent;

import com.devp.agent.config.AiProperties;
import com.devp.agent.support.PostgresTestcontainer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards that no automated test can call a real model.
 *
 * <p>Real model calls cost money and quota and are made exclusively by the user through
 * the UI. The protection lives in {@code src/test/resources/application-test.properties};
 * this test makes sure it works — even when {@code LOCAL_AI_BASE_URL} or
 * {@code OPENAI_API_KEY} are set in the terminal.
 *
 * <p>If it fails, a provider points to a real endpoint. Then fix the cause, do not adjust
 * this test.
 */
@SpringBootTest
@Import(PostgresTestcontainer.class)
class NoRealModelCallsTests {

	@Autowired
	private AiProperties ai;

	@Test
	void noProviderPointsToARealEndpoint() {
		assertThat(ai.getProviders()).isNotEmpty();

		ai.getProviders().forEach((id, provider) -> assertThat(provider.getBaseUrl())
				.as("provider '%s' must point to a dead address in tests", id)
				.isEqualTo("http://127.0.0.1:1/v1"));
	}

	@Test
	void noProviderCarriesARealKey() {
		ai.getProviders().forEach((id, provider) -> assertThat(provider.getApiKey())
				.as("provider '%s' must not carry a real API key in tests", id)
				.isIn("not-set", "not-needed"));
	}
}
