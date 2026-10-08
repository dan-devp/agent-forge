package com.devp.forge.doc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownWriterTests {

	@Test
	void roundTripsFrontMatterAndBody() {
		Map<String, Object> fm = new LinkedHashMap<>();
		fm.put("id", "demo");
		fm.put("version", 3);
		fm.put("temperature", 0.7);
		fm.put("local", true);
		fm.put("tools", List.of("currentTime", "divide"));
		Map<String, Object> eval = new LinkedHashMap<>();
		eval.put("type", "regex");
		eval.put("pattern", "\\b14\\b");
		fm.put("evals", List.of(eval));

		String written = MarkdownWriter.write(fm, "First line.\n\n# Section\n\nSecond line.");
		MarkdownDocument read = MarkdownParser.parse(written, "test");

		assertThat(read.id()).isEqualTo("demo");
		assertThat(read.integer("version")).isEqualTo(3);
		assertThat(read.decimal("temperature")).isEqualTo(0.7);
		assertThat(read.bool("local", false)).isTrue();
		assertThat(read.strings("tools")).containsExactly("currentTime", "divide");
		assertThat(read.objects("evals")).hasSize(1);
		// The backslashes survive: YAML quoting is the writer's job, not the author's.
		assertThat(read.objects("evals").getFirst().get("pattern")).isEqualTo("\\b14\\b");
		assertThat(read.body()).isEqualTo("First line.\n\n# Section\n\nSecond line.");
	}

	@Test
	void keepsKeyOrder() {
		Map<String, Object> fm = new LinkedHashMap<>();
		fm.put("id", "x");
		fm.put("version", 1);
		fm.put("name", "X");
		fm.put("base-url", "http://h/v1");

		String written = MarkdownWriter.write(fm, null);

		assertThat(written.indexOf("id:")).isLessThan(written.indexOf("version:"));
		assertThat(written.indexOf("version:")).isLessThan(written.indexOf("name:"));
		assertThat(written.indexOf("name:")).isLessThan(written.indexOf("base-url:"));
		assertThat(written).endsWith("---\n");
	}

	@Test
	void emptyBodyWritesNoTrailingText() {
		String written = MarkdownWriter.write(Map.of("id", "x"), "   ");
		assertThat(written).isEqualTo("---\nid: x\n---\n");
		assertThat(MarkdownParser.parse(written, "t").body()).isEmpty();
	}
}
