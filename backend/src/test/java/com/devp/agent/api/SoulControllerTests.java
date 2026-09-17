package com.devp.agent.api;

import com.devp.agent.config.SoulProperties;
import com.devp.agent.soul.SoulRenderer;
import com.devp.agent.soul.SoulRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(SoulController.class)
@Import(SoulControllerTests.Beans.class)
class SoulControllerTests {

	@Autowired
	private MockMvcTester mvc;

	@TestConfiguration
	static class Beans {

		@Bean
		SoulProperties soulProperties() {
			SoulProperties properties = new SoulProperties();
			properties.setActive("forge");
			return properties;
		}

		@Bean
		SoulRepository soulRepository(SoulProperties properties) {
			return new SoulRepository(properties);
		}

		@Bean
		SoulRenderer soulRenderer() {
			return new SoulRenderer();
		}
	}

	@Test
	void listsSoulsWithActiveMarker() {
		assertThat(mvc.get().uri("/api/souls")).hasStatusOk()
				.bodyJson()
				.extractingPath("$.activeSoul").isEqualTo("forge");

		assertThat(mvc.get().uri("/api/souls")).bodyJson()
				.extractingPath("$.souls[?(@.id == 'forge')].isActive").asArray().containsExactly(true);
	}

	/** The detail endpoint deliberately includes the rendered prompt — otherwise what the
	 * model actually sees cannot be verified. */
	@Test
	void returnsRenderedPrompt() {
		assertThat(mvc.get().uri("/api/souls/reviewer")).hasStatusOk()
				.bodyJson()
				.extractingPath("$.prompt").asString()
				.contains("Du bist Argus (Kritischer Reviewer).")
				.contains("# Pflichtfragen");
	}

	@Test
	void unknownSoulReturns404() {
		assertThat(mvc.get().uri("/api/souls/does-not-exist")).hasStatus(404)
				.bodyJson()
				.extractingPath("$.title").isEqualTo("Unknown SOUL");
	}

	@Test
	void reloadReturnsTheList() {
		assertThat(mvc.post().uri("/api/souls/reload")).hasStatusOk()
				.bodyJson()
				.extractingPath("$.souls").asArray().isNotEmpty();
	}
}
