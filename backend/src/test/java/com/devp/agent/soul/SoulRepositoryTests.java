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
	void laedtMitgelieferteSouls() {
		SoulRepository repository = repository("forge", "classpath:souls/");

		assertThat(repository.list()).extracting(Soul::id).contains("forge", "reviewer");
		assertThat(repository.activeId()).isEqualTo("forge");
	}

	@Test
	void loestLeereIdAufDieAktiveSoulAuf() {
		SoulRepository repository = repository("reviewer", "classpath:souls/");

		assertThat(repository.resolve(null).id()).isEqualTo("reviewer");
		assertThat(repository.resolve("  ").id()).isEqualTo("reviewer");
		assertThat(repository.resolve("forge").id()).isEqualTo("forge");
	}

	@Test
	void meldetUnbekannteSoul() {
		SoulRepository repository = repository("forge", "classpath:souls/");

		assertThatThrownBy(() -> repository.resolve("gibtsnicht"))
				.isInstanceOf(UnknownSoulException.class)
				.hasMessageContaining("forge");
	}

	/** Ohne Definition muss der Agent antwortfaehig bleiben, statt beim Aufruf zu scheitern. */
	@Test
	void faelltOhneDefinitionAufDenNotfallZurueck() {
		SoulRepository repository = repository("forge", "classpath:gibtsnicht/");

		assertThat(repository.list()).extracting(Soul::id).containsExactly("default");
		assertThat(repository.resolve(null).id()).isEqualTo("default");
		assertThat(repository.activeId()).isEqualTo("default");
	}

	/** Spaetere Location gewinnt — so ist eine SOUL im Betrieb austauschbar. */
	@Test
	void externeLocationUeberschreibtGleicheId(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("forge.md"), """
				---
				id: forge
				name: Ersetzt
				version: 9
				---
				# Identitaet
				Andere Identitaet.
				""");

		SoulRepository repository = repository("forge", "classpath:souls/", dir.toUri().toString());

		Soul forge = repository.resolve("forge");
		assertThat(forge.name()).isEqualTo("Ersetzt");
		assertThat(forge.version()).isEqualTo(9);
		// Die nicht ueberschriebene SOUL bleibt erhalten.
		assertThat(repository.resolve("reviewer").name()).isEqualTo("Argus");
	}

	@Test
	void reloadLiestNeueDateienEin(@TempDir Path dir) throws Exception {
		SoulRepository repository = repository("forge", dir.toUri().toString());
		assertThat(repository.list()).extracting(Soul::id).containsExactly("default");

		Files.writeString(dir.resolve("neu.md"), """
				---
				id: neu
				name: Neu
				---
				# Identitaet
				Frisch geladen.
				""");

		assertThat(repository.reload()).isEqualTo(1);
		assertThat(repository.resolve("neu").name()).isEqualTo("Neu");
	}
}
