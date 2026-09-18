package com.devp.agent.agent;

import com.devp.agent.agent.Agent.ToolAccess.Mode;
import com.devp.agent.agent.AgentParser.AgentParseException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentParserTests {

	@Test
	void readsModelIdentityAndAllowlist() {
		Agent agent = AgentParser.parse("""
				---
				id: analyst
				name: Analyst
				version: 3
				description: Reads, does not write.
				provider: local
				soul: reviewer
				tools:
				  - currentTime
				  - divide
				---
				# Zweck
				Prosa fuer Menschen.
				""", "analyst.md");

		assertThat(agent.id()).isEqualTo("analyst");
		assertThat(agent.version()).isEqualTo(3);
		assertThat(agent.provider()).isEqualTo("local");
		assertThat(agent.soul()).isEqualTo("reviewer");
		assertThat(agent.tools().mode()).isEqualTo(Mode.ALLOWLIST);
		assertThat(agent.tools().names()).containsExactly("currentTime", "divide");
		assertThat(agent.sections()).containsKey("Zweck");
	}

	@Test
	void allowlistDecidesPerTool() {
		Agent agent = AgentParser.parse("""
				---
				id: narrow
				tools: [divide]
				---
				""", "narrow.md");

		assertThat(agent.tools().allows("divide")).isTrue();
		assertThat(agent.tools().allows("currentTime")).isFalse();
	}

	/** Three deliberate statements, not one nullable list — and the UI must be able to show which. */
	@Test
	void allAndNoneAreTheirOwnModes() {
		assertThat(AgentParser.parse("---\nid: a\ntools: all\n---\n", "a.md").tools().mode()).isEqualTo(Mode.ALL);
		assertThat(AgentParser.parse("---\nid: b\ntools: none\n---\n", "b.md").tools().mode()).isEqualTo(Mode.NONE);
		assertThat(AgentParser.parse("---\nid: c\ntools: []\n---\n", "c.md").tools().mode()).isEqualTo(Mode.NONE);
	}

	/** An agent file written before governance existed keeps working — visibly as ALL. */
	@Test
	void missingToolsKeyMeansEveryTool() {
		Agent agent = AgentParser.parse("---\nid: old\nsoul: forge\n---\n", "old.md");

		assertThat(agent.tools().mode()).isEqualTo(Mode.ALL);
		assertThat(agent.tools().allows("anything")).isTrue();
	}

	@Test
	void versionDefaultsToOneAndNameToId() {
		Agent agent = AgentParser.parse("---\nid: plain\n---\n", "plain.md");

		assertThat(agent.version()).isEqualTo(1);
		assertThat(agent.name()).isEqualTo("plain");
	}

	@Test
	void namesTheSourceWhenTheFileIsBroken() {
		assertThatThrownBy(() -> AgentParser.parse("no front matter", "broken.md"))
				.isInstanceOf(AgentParseException.class)
				.hasMessageContaining("broken.md");
	}
}
