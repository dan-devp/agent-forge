package com.devp.agent.task;

import com.devp.agent.eval.CriterionType;
import com.devp.agent.task.TaskParser.TaskParseException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskParserTests {

	private static final String COMPLETE = """
			---
			id: time
			name: Zeit ermitteln
			version: 4
			description: test definition
			difficulty: 3
			tags: [tools, builtin]
			soul: reviewer
			provider: local
			max-tool-calls: 5
			max-duration-ms: 30000
			success:
			  - type: tool-called
			    value: currentTime
			  - type: regex
			    value: '\\d{2}:\\d{2}'
			    description: time of day
			---

			# Goal

			Wie spaet ist es in UTC?

			# Background

			Checks whether the tool is used.
			""";

	@Test
	void readsFrontMatterAndGoal() {
		Task task = TaskParser.parse(COMPLETE, "time.md");

		assertThat(task.id()).isEqualTo("time");
		assertThat(task.name()).isEqualTo("Zeit ermitteln");
		assertThat(task.version()).isEqualTo(4);
		assertThat(task.description()).isEqualTo("test definition");
		assertThat(task.difficulty()).isEqualTo(3);
		assertThat(task.tags()).containsExactly("tools", "builtin");
		assertThat(task.goal()).isEqualTo("Wie spaet ist es in UTC?");
		assertThat(task.source()).isEqualTo("time.md");
	}

	/** Defaults of the task that a call may override. */
	@Test
	void readsDefaultsAndBudget() {
		Task task = TaskParser.parse(COMPLETE, "time.md");

		assertThat(task.soul()).isEqualTo("reviewer");
		assertThat(task.provider()).isEqualTo("local");
		assertThat(task.maxToolCalls()).isEqualTo(5);
		assertThat(task.maxDurationMs()).isEqualTo(30_000L);
	}

	/** The rubric belongs to the task so it is versioned together with it. */
	@Test
	void readsSuccessCriteria() {
		Task task = TaskParser.parse(COMPLETE, "time.md");

		assertThat(task.success()).hasSize(2);
		assertThat(task.success().get(0).type()).isEqualTo(CriterionType.TOOL_CALLED);
		assertThat(task.success().get(0).value()).isEqualTo("currentTime");
		assertThat(task.success().get(1).description()).isEqualTo("time of day");
	}

	@Test
	void reportsBrokenCriterionWithSource() {
		assertThatThrownBy(() -> TaskParser.parse("""
				---
				id: x
				success:
				  - type: unknown
				    value: y
				---
				# Goal
				Do something.
				""", "x.md"))
				.isInstanceOf(TaskParseException.class)
				.hasMessageContaining("x.md")
				.hasMessageContaining("unknown");
	}

	@Test
	void keepsFurtherSections() {
		Task task = TaskParser.parse(COMPLETE, "time.md");

		assertThat(task.sections().keySet()).containsExactly("Goal", "Background");
		assertThat(task.sections().get("Background")).contains("Checks");
	}

	@Test
	void usesDefaultsWhenNothingGiven() {
		Task task = TaskParser.parse("""
				---
				id: minimal
				---
				# Goal
				Do something.
				""", "minimal.md");

		assertThat(task.name()).isEqualTo("minimal");
		assertThat(task.version()).isEqualTo(1);
		assertThat(task.difficulty()).isEqualTo(1);
		assertThat(task.tags()).isEmpty();
		assertThat(task.soul()).isNull();
		assertThat(task.maxToolCalls()).isNull();
		assertThat(task.maxDurationMs()).isNull();
		assertThat(task.success()).isEmpty();
	}

	@Test
	void fallsBackToFileNameWithoutId() {
		Task task = TaskParser.parse("""
				---
				name: Without id
				---
				# Goal
				Do something.
				""", "tasks/important.md");

		assertThat(task.id()).isEqualTo("important");
	}

	/** Without a goal there is nothing to execute — that must surface on load, not in the run. */
	@Test
	void reportsMissingGoal() {
		assertThatThrownBy(() -> TaskParser.parse("""
				---
				id: empty
				---
				# Background
				Prose only.
				""", "empty.md"))
				.isInstanceOf(TaskParseException.class)
				.hasMessageContaining("empty.md")
				.hasMessageContaining("Goal");
	}

	@Test
	void reportsEmptyGoal() {
		assertThatThrownBy(() -> TaskParser.parse("---\nid: empty\n---\n# Goal\n\n", "empty.md"))
				.isInstanceOf(TaskParseException.class)
				.hasMessageContaining("Goal");
	}

	@Test
	void reportsMissingFrontMatter() {
		assertThatThrownBy(() -> TaskParser.parse("# Goal\nDo something.", "broken.md"))
				.isInstanceOf(TaskParseException.class)
				.hasMessageContaining("front matter");
	}

	@Test
	void reportsNonNumericDifficulty() {
		assertThatThrownBy(() -> TaskParser.parse("""
				---
				id: x
				difficulty: hard
				---
				# Goal
				Do something.
				""", "x.md"))
				.isInstanceOf(TaskParseException.class)
				.hasMessageContaining("difficulty");
	}

	@Test
	void acceptsSingleTagWithoutList() {
		Task task = TaskParser.parse("""
				---
				id: x
				tags: tools
				---
				# Goal
				Do something.
				""", "x.md");

		assertThat(task.tags()).containsExactly("tools");
	}
}
