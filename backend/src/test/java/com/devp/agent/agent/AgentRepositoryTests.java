package com.devp.agent.agent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.devp.agent.agent.Agent.ToolAccess.Mode;
import com.devp.agent.agent.AgentRepository.UnknownAgentException;
import com.devp.agent.config.AgentCatalogProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRepositoryTests {

	private static AgentRepository repository(String defaultAgent, String... locations) {
		AgentCatalogProperties properties = new AgentCatalogProperties();
		properties.setDefaultAgent(defaultAgent);
		properties.setLocations(List.of(locations));
		return new AgentRepository(properties);
	}

	@Test
	void loadsBundledAgents() {
		AgentRepository repository = repository("forge", "classpath:agents/");

		assertThat(repository.list()).extracting(Agent::id).contains("forge", "forge-tools", "argus");
		assertThat(repository.resolve("argus").tools().mode()).isEqualTo(Mode.NONE);
		assertThat(repository.resolve("forge-tools").tools().names()).containsExactly("currentTime", "divide");
		assertThat(repository.defaultId()).isEqualTo("forge");
	}

	@Test
	void resolvesTheDefaultWhenNoneIsNamed() {
		assertThat(repository("argus", "classpath:agents/").resolve(null).id()).isEqualTo("argus");
	}

	@Test
	void unknownAgentNamesTheAvailableOnes() {
		assertThatThrownBy(() -> repository("forge", "classpath:agents/").resolve("ghost"))
				.isInstanceOf(UnknownAgentException.class)
				.hasMessageContaining("ghost")
				.hasMessageContaining("forge");
	}

	/** An external location wins for the same id — an agent is replaceable without a rebuild. */
	@Test
	void externalLocationOverridesTheBundledAgent(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("forge.md"), """
				---
				id: forge
				name: Forge (extern)
				version: 9
				tools: none
				---
				""");

		AgentRepository repository = repository("forge", "classpath:agents/", "file:" + dir.toUri().getPath());

		Agent forge = repository.resolve("forge");
		assertThat(forge.version()).isEqualTo(9);
		assertThat(forge.tools().mode()).isEqualTo(Mode.NONE);
	}

	/** An empty catalogue must not stop the application — the fallback behaves like before agents existed. */
	@Test
	void fallsBackWhenNoAgentFileExists(@TempDir Path dir) {
		AgentRepository repository = repository("forge", "file:" + dir.toUri().getPath());

		assertThat(repository.list()).hasSize(1);
		assertThat(repository.resolve("whatever").tools().mode()).isEqualTo(Mode.ALL);
	}
}
