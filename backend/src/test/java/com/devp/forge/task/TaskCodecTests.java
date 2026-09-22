package com.devp.forge.task;

import java.util.List;

import com.devp.forge.doc.MarkdownParser;
import com.devp.forge.doc.MarkdownWriter;
import com.devp.forge.eval.EvalRegistry;
import com.devp.forge.eval.TextEvals;
import com.devp.forge.eval.ToolEvals;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskCodecTests {

	private final TaskCodec codec = new TaskCodec(
			new EvalRegistry(List.of(new TextEvals.Regex(), new ToolEvals.ToolCalled(), new TextEvals.Contains())));

	private static final String FILE = """
			---
			id: time
			version: 2
			name: Zeit
			max-tool-calls: 4
			evals:
			  - type: tool-called
			    tool: currentTime
			  - type: regex
			    pattern: '\\b14\\b'
			    ignore-case: true
			---
			Wie spaet ist es?

			# Hinweis
			Nutze das Werkzeug.
			""";

	@Test
	void parsesEvalsWithTheirParameters() {
		Task task = codec.decode(MarkdownParser.parse(FILE, "t"));

		assertThat(task.id()).isEqualTo("time");
		assertThat(task.version()).isEqualTo(2);
		assertThat(task.maxToolCalls()).isEqualTo(4);
		assertThat(task.evals()).hasSize(2);
		assertThat(task.evals().get(0).type()).isEqualTo("tool-called");
		assertThat(task.evals().get(0).string("tool")).isEqualTo("currentTime");
		assertThat(task.evals().get(1).string("pattern")).isEqualTo("\\b14\\b");
		assertThat(task.evals().get(1).bool("ignore-case", false)).isTrue();
		// The whole body is the input, headings included.
		assertThat(task.input()).startsWith("Wie spaet ist es?").contains("# Hinweis");
	}

	@Test
	void roundTrips() {
		Task task = codec.decode(MarkdownParser.parse(FILE, "t"));
		String written = MarkdownWriter.write(codec.frontMatter(task), codec.body(task));
		Task again = codec.decode(MarkdownParser.parse(written, "t2"));

		assertThat(again).isEqualTo(task);
	}

	@Test
	void rejectsUnknownEvalTypeAndMissingParameter() {
		String badType = FILE.replace("type: regex", "type: judge");
		assertThatThrownBy(() -> codec.decode(MarkdownParser.parse(badType, "t")))
				.isInstanceOf(EntityInvalidException.class).hasMessageContaining("eval #2");

		String missing = FILE.replace("    tool: currentTime\n", "");
		assertThatThrownBy(() -> codec.decode(MarkdownParser.parse(missing, "t")))
				.isInstanceOf(EntityInvalidException.class).hasMessageContaining("'tool' is required");
	}

	@Test
	void rejectsEmptyInput() {
		String empty = FILE.substring(0, FILE.lastIndexOf("---") + 3) + "\n";
		assertThatThrownBy(() -> codec.decode(MarkdownParser.parse(empty, "t")))
				.isInstanceOf(EntityInvalidException.class).hasMessageContaining("input");
	}
}
