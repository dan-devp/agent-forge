package com.devp.agent.eval;

import java.util.List;

import com.devp.agent.doc.MarkdownParser;
import com.devp.agent.doc.MarkdownParser.MarkdownParseException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CriterionParserTests {

	private static List<Criterion> parse(String frontMatter) {
		return CriterionParser.parse(MarkdownParser.parse("---\nid: x\n" + frontMatter + "\n---\n# Goal\nDo.", "x.md"));
	}

	@Test
	void readsAllTypes() {
		List<Criterion> criteria = parse("""
				success:
				  - type: contains
				    value: Rest 2
				    description: quotient
				  - type: not-contains
				    value: Euro
				  - type: regex
				    value: '\\d+'
				  - type: not-regex
				    value: '€'
				  - type: tool-called
				    value: divide
				  - type: tool-not-called
				    value: '*'
				""");

		assertThat(criteria).extracting(Criterion::type).containsExactly(CriterionType.values());
		assertThat(criteria.getFirst().value()).isEqualTo("Rest 2");
		assertThat(criteria.getFirst().description()).isEqualTo("quotient");
		assertThat(criteria.get(1).description()).isNull();
	}

	@Test
	void emptyWithoutBlock() {
		assertThat(parse("name: x")).isEmpty();
	}

	@Test
	void reportsUnknownType() {
		assertThatThrownBy(() -> parse("success:\n  - type: smells-nice\n    value: x"))
				.isInstanceOf(MarkdownParseException.class)
				.hasMessageContaining("success[1]")
				.hasMessageContaining("smells-nice")
				.hasMessageContaining("tool-called");
	}

	@Test
	void reportsMissingValue() {
		assertThatThrownBy(() -> parse("success:\n  - type: contains"))
				.isInstanceOf(MarkdownParseException.class)
				.hasMessageContaining("'value' is missing");
	}

	/** A broken expression must surface on load, not silently fail every run. */
	@Test
	void reportsInvalidRegexOnLoad() {
		assertThatThrownBy(() -> parse("success:\n  - type: regex\n    value: '(unclosed'"))
				.isInstanceOf(MarkdownParseException.class)
				.hasMessageContaining("regular expression");
	}

	@Test
	void reportsNonObjectEntry() {
		assertThatThrownBy(() -> parse("success:\n  - just-text"))
				.isInstanceOf(MarkdownParseException.class)
				.hasMessageContaining("object");
	}
}
