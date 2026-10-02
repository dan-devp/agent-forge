package com.devp.forge.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionClientRegistryTests {

	@Test
	void theSdkGetsTheApiRootWithoutTheVersionPath() {
		assertThat(DecisionClientRegistry.apiRoot("https://api.typesafe.ai/v1")).isEqualTo("https://api.typesafe.ai");
		assertThat(DecisionClientRegistry.apiRoot("http://127.0.0.1:1/v1/")).isEqualTo("http://127.0.0.1:1");
		assertThat(DecisionClientRegistry.apiRoot("http://gateway:8080")).isEqualTo("http://gateway:8080");
	}
}
