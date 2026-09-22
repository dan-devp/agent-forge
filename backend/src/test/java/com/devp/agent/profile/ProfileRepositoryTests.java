package com.devp.agent.profile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.devp.agent.config.ProfileProperties;
import com.devp.agent.profile.ProfileParser.ProfileParseException;
import com.devp.agent.profile.ProfileRepository.UnknownProfileException;
import com.devp.agent.strategy.ExampleStrategy;
import com.devp.agent.strategy.ReflectionStrategy;
import com.devp.agent.strategy.RequireTextStrategy;
import com.devp.agent.strategy.SelfCheckStrategy;
import com.devp.agent.strategy.StrategyRegistry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileRepositoryTests {

	// Every strategy the application registers — the bundled profiles may name all of them.
	private static final StrategyRegistry REGISTRY = new StrategyRegistry(List.of(new RequireTextStrategy(),
			new ReflectionStrategy(), new SelfCheckStrategy(), new ExampleStrategy()));

	private static ProfileRepository repository(String defaultProfile, String... locations) {
		ProfileProperties properties = new ProfileProperties();
		properties.setDefaultProfile(defaultProfile);
		properties.setLocations(List.of(locations));
		return new ProfileRepository(properties, REGISTRY);
	}

	@Test
	void loadsBundledProfiles() {
		ProfileRepository repository = repository("baseline", "classpath:profiles/");

		assertThat(repository.list()).extracting(Profile::id).contains("baseline", "require-text-demo");
		assertThat(repository.resolve("baseline").strategies()).isEmpty();
		assertThat(repository.defaultId()).isEqualTo("baseline");
	}

	/** Every bundled profile must only name registered strategies — checked on load, not in a run. */
	@Test
	void bundledProfilesNameOnlyKnownStrategies() {
		ProfileRepository repository = repository("baseline", "classpath:profiles/");

		assertThat(repository.list()).allSatisfy(profile -> profile.strategies()
				.forEach(config -> assertThat(REGISTRY.knows(config.id())).as(config.id()).isTrue()));
	}

	@Test
	void resolvesBlankIdToTheDefault() {
		ProfileRepository repository = repository("require-text-demo", "classpath:profiles/");

		assertThat(repository.resolve(null).id()).isEqualTo("require-text-demo");
		assertThat(repository.resolve("").id()).isEqualTo("require-text-demo");
		assertThat(repository.resolve("baseline").id()).isEqualTo("baseline");
	}

	@Test
	void reportsUnknownProfile() {
		assertThatThrownBy(() -> repository("baseline", "classpath:profiles/").resolve("does-not-exist"))
				.isInstanceOf(UnknownProfileException.class)
				.hasMessageContaining("baseline");
	}

	/** Without a file the agent still runs — as baseline, with nothing active. */
	@Test
	void fallsBackToEmptyBaselineWithoutFiles() {
		ProfileRepository repository = repository("baseline", "classpath:does-not-exist/");

		assertThat(repository.list()).extracting(Profile::id).containsExactly("baseline");
		assertThat(repository.resolve(null).strategies()).isEmpty();
	}

	/** A profile naming an unknown strategy is a defect in the file — it must fail on load. */
	@Test
	void rejectsUnknownStrategyOnLoad(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("broken.md"), """
				---
				id: broken
				strategies:
				  - id: does-not-exist
				---
				""");

		assertThatThrownBy(() -> repository("baseline", dir.toUri().toString()))
				.isInstanceOf(ProfileParseException.class)
				.hasMessageContaining("broken.md")
				.hasMessageContaining("does-not-exist")
				.hasMessageContaining("require-text");
	}

	@Test
	void externalLocationOverridesSameId(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("baseline.md"), """
				---
				id: baseline
				name: Replaced
				version: 9
				strategies:
				  - id: require-text
				    text: x
				---
				""");

		ProfileRepository repository = repository("baseline", "classpath:profiles/", dir.toUri().toString());

		Profile baseline = repository.resolve("baseline");
		assertThat(baseline.name()).isEqualTo("Replaced");
		assertThat(baseline.version()).isEqualTo(9);
		assertThat(baseline.strategies()).hasSize(1);
	}
}
