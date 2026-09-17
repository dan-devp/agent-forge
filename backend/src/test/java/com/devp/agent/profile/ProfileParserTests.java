package com.devp.agent.profile;

import com.devp.agent.profile.Profile.StrategyConfig;
import com.devp.agent.profile.ProfileParser.ProfileParseException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileParserTests {

	private static final String COMPLETE = """
			---
			id: reflexion
			name: Reflexion
			version: 2
			description: test definition
			strategies:
			  - id: reflexion
			    max-notes: 5
			    scope: task
			  - id: require-text
			    text: Rest
			---

			# Purpose

			Prose.
			""";

	@Test
	void readsFrontMatterAndStrategies() {
		Profile profile = ProfileParser.parse(COMPLETE, "reflexion.md");

		assertThat(profile.id()).isEqualTo("reflexion");
		assertThat(profile.name()).isEqualTo("Reflexion");
		assertThat(profile.version()).isEqualTo(2);
		assertThat(profile.description()).isEqualTo("test definition");
		assertThat(profile.source()).isEqualTo("reflexion.md");
		assertThat(profile.sections()).containsOnlyKeys("Purpose");
		assertThat(profile.strategies()).extracting(StrategyConfig::id).containsExactly("reflexion", "require-text");
	}

	/** Every key besides id is a parameter — the parser does not know the strategy's vocabulary. */
	@Test
	void keepsParametersPerStrategy() {
		Profile profile = ProfileParser.parse(COMPLETE, "reflexion.md");

		StrategyConfig reflexion = profile.strategies().getFirst();
		assertThat(reflexion.params()).containsOnlyKeys("max-notes", "scope");
		assertThat(reflexion.intParam("max-notes", 1)).isEqualTo(5);
		assertThat(reflexion.param("scope")).isEqualTo("task");
		assertThat(reflexion.param("missing", "fallback")).isEqualTo("fallback");
		assertThat(reflexion.intParam("missing", 7)).isEqualTo(7);
	}

	/** baseline: an explicitly empty list, so the file says "none" instead of forgetting the key. */
	@Test
	void acceptsEmptyStrategyList() {
		Profile profile = ProfileParser.parse("---\nid: baseline\nstrategies: []\n---\n", "baseline.md");

		assertThat(profile.strategies()).isEmpty();
		assertThat(profile.version()).isEqualTo(1);
	}

	@Test
	void missingKeyMeansNoStrategies() {
		assertThat(ProfileParser.parse("---\nid: x\n---\n", "x.md").strategies()).isEmpty();
	}

	@Test
	void reportsStrategyWithoutId() {
		assertThatThrownBy(() -> ProfileParser.parse("---\nid: x\nstrategies:\n  - text: Rest\n---\n", "x.md"))
				.isInstanceOf(ProfileParseException.class)
				.hasMessageContaining("x.md")
				.hasMessageContaining("strategies[1]")
				.hasMessageContaining("'id' is missing");
	}

	@Test
	void reportsNonObjectEntry() {
		assertThatThrownBy(() -> ProfileParser.parse("---\nid: x\nstrategies:\n  - reflexion\n---\n", "x.md"))
				.isInstanceOf(ProfileParseException.class)
				.hasMessageContaining("object");
	}

	@Test
	void reportsMissingFrontMatter() {
		assertThatThrownBy(() -> ProfileParser.parse("# Purpose\nProse.", "broken.md"))
				.isInstanceOf(ProfileParseException.class)
				.hasMessageContaining("front matter");
	}
}
