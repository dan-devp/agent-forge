package com.devp.agent.soul;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.devp.agent.config.SoulProperties;
import com.devp.agent.soul.SoulRepository.UnknownSoulException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SoulRepositoryTests {

	private static SoulRepository repository(String active, String... locations) {
		SoulProperties properties = new SoulProperties();
		properties.setActive(active);
		properties.setLocations(List.of(locations));
		return new SoulRepository(properties);
	}

	@Test
	void loadsBundledSouls() {
		SoulRepository repository = repository("forge", "classpath:souls/");

		assertThat(repository.list()).extracting(Soul::id).contains("forge", "reviewer");
		assertThat(repository.activeId()).isEqualTo("forge");
	}

	@Test
	void resolvesBlankIdToTheActiveSoul() {
		SoulRepository repository = repository("reviewer", "classpath:souls/");

		assertThat(repository.resolve(null).id()).isEqualTo("reviewer");
		assertThat(repository.resolve("  ").id()).isEqualTo("reviewer");
		assertThat(repository.resolve("forge").id()).isEqualTo("forge");
	}

	@Test
	void reportsUnknownSoul() {
		SoulRepository repository = repository("forge", "classpath:souls/");

		assertThatThrownBy(() -> repository.resolve("does-not-exist"))
				.isInstanceOf(UnknownSoulException.class)
				.hasMessageContaining("forge");
	}

	/** Without a definition the agent must stay able to answer instead of failing on the call. */
	@Test
	void fallsBackWithoutDefinition() {
		SoulRepository repository = repository("forge", "classpath:does-not-exist/");

		assertThat(repository.list()).extracting(Soul::id).containsExactly("default");
		assertThat(repository.resolve(null).id()).isEqualTo("default");
		assertThat(repository.activeId()).isEqualTo("default");
	}

	/** A later location wins — so a SOUL is replaceable in operation. */
	@Test
	void externalLocationOverridesSameId(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("forge.md"), """
				---
				id: forge
				name: Replaced
				version: 9
				---
				# Identitaet
				Andere Identitaet.
				""");

		SoulRepository repository = repository("forge", "classpath:souls/", dir.toUri().toString());

		Soul forge = repository.resolve("forge");
		assertThat(forge.name()).isEqualTo("Replaced");
		assertThat(forge.version()).isEqualTo(9);
		// The SOUL that was not overridden stays intact.
		assertThat(repository.resolve("reviewer").name()).isEqualTo("Argus");
	}

	@Test
	void reloadPicksUpNewFiles(@TempDir Path dir) throws Exception {
		SoulRepository repository = repository("forge", dir.toUri().toString());
		assertThat(repository.list()).extracting(Soul::id).containsExactly("default");

		Files.writeString(dir.resolve("new.md"), """
				---
				id: new
				name: New
				---
				# Identitaet
				Frisch geladen.
				""");

		assertThat(repository.reload()).isEqualTo(1);
		assertThat(repository.resolve("new").name()).isEqualTo("New");
	}
}
