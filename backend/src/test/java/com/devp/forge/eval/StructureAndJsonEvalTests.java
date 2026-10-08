package com.devp.forge.eval;

import java.util.LinkedHashMap;
import java.util.List;

import com.devp.forge.store.StoreExceptions.EntityInvalidException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructureAndJsonEvalTests {

	private static EvalConfig cfg(String type, Object... kv) {
		var params = new LinkedHashMap<String, Object>();
		for (int i = 0; i < kv.length; i += 2) {
			params.put((String) kv[i], kv[i + 1]);
		}
		return new EvalConfig(type, params);
	}

	private static EvalSubject answer(String text, String... tools) {
		return new EvalSubject("Question", text, "DONE", List.of(tools));
	}

	@Test
	void equalsNormalizesCaseAndWhitespaceByDefault() {
		Eval eval = new StructureEvals.Equals();
		assertThat(eval.evaluate(cfg("equals", "text", "Paris"), answer("  paris \n")).passed()).isTrue();
		assertThat(eval.evaluate(cfg("equals", "text", "a b"), answer("a   b")).passed()).isTrue();
		assertThat(eval.evaluate(cfg("equals", "text", "Paris", "ignore-case", false), answer("paris")).passed())
				.isFalse();
		assertThat(eval.evaluate(cfg("equals", "text", "Paris"), answer("Paris it is.")).passed()).isFalse();
	}

	@Test
	void lengthCountsWordsOrChars() {
		Eval eval = new StructureEvals.Length();
		assertThat(eval.evaluate(cfg("length", "max", 3), answer("one two three")).passed()).isTrue();
		assertThat(eval.evaluate(cfg("length", "max", 2), answer("one two three")).passed()).isFalse();
		assertThat(eval.evaluate(cfg("length", "min", 5, "unit", "chars"), answer("abcd")).passed()).isFalse();
		assertThat(eval.evaluate(cfg("length", "min", 2, "max", 4), answer("a b c")).passed()).isTrue();
		assertThatThrownBy(() -> eval.evaluate(cfg("length"), answer("x"))).isInstanceOf(EntityInvalidException.class);
		assertThatThrownBy(() -> eval.evaluate(cfg("length", "max", 1, "unit", "lines"), answer("x")))
				.isInstanceOf(EntityInvalidException.class);
	}

	@Test
	void maxToolCallsCountsTheTrace() {
		Eval eval = new StructureEvals.MaxToolCalls();
		assertThat(eval.evaluate(cfg("max-tool-calls", "max", 2), answer("x", "a", "b")).passed()).isTrue();
		assertThat(eval.evaluate(cfg("max-tool-calls", "max", 1), answer("x", "a", "b")).passed()).isFalse();
		assertThat(eval.evaluate(cfg("max-tool-calls", "max", 0), answer(null)).passed()).isTrue();
	}

	@Test
	void jsonIsFoundInProseAndFences() {
		assertThat(JsonEvals.extract("{\"a\":1}")).isPresent();
		assertThat(JsonEvals.extract("Here is the JSON:\n```json\n{\"a\": 1}\n```\nDone.")).isPresent();
		assertThat(JsonEvals.extract("Result: {\"a\": [1, 2]} — there you go.")).isPresent();
		assertThat(JsonEvals.extract("no json here")).isEmpty();
		assertThat(JsonEvals.extract("42")).isEmpty();
	}

	@Test
	void jsonValidStrictRejectsSurroundingText() {
		Eval eval = new JsonEvals.JsonValid();
		assertThat(eval.evaluate(cfg("json-valid"), answer("Answer: {\"a\":1}")).passed()).isTrue();
		assertThat(eval.evaluate(cfg("json-valid", "strict", true), answer("Answer: {\"a\":1}")).passed()).isFalse();
		assertThat(eval.evaluate(cfg("json-valid", "strict", true), answer("{\"a\":1}")).passed()).isTrue();
	}

	@Test
	void jsonPathWalksObjectsAndArrays() {
		Eval eval = new JsonEvals.JsonPath();
		String json = "{\"items\":[{\"name\":\"pen\",\"price\":2.5}],\"total\":2.5,\"ok\":true}";
		assertThat(eval.evaluate(cfg("json-path", "path", "items[0].name", "value", "PEN"), answer(json)).passed())
				.isTrue();
		assertThat(eval.evaluate(cfg("json-path", "path", "total", "value", "2.5"), answer(json)).passed()).isTrue();
		assertThat(eval.evaluate(cfg("json-path", "path", "ok", "value", "true"), answer(json)).passed()).isTrue();
		assertThat(eval.evaluate(cfg("json-path", "path", "items[0]"), answer(json)).passed()).isTrue();
		assertThat(eval.evaluate(cfg("json-path", "path", "items[1].name"), answer(json)).passed()).isFalse();
		assertThat(eval.evaluate(cfg("json-path", "path", "total", "value", "3"), answer(json)).passed()).isFalse();
		assertThat(eval.evaluate(cfg("json-path", "path", "total"), answer("no json")).passed()).isFalse();
	}

	@Test
	void registryKnowsAllElevenTypes() {
		EvalRegistry registry = new EvalRegistry(List.of(
				new TextEvals.Contains(), new TextEvals.NotContains(), new TextEvals.Regex(), new TextEvals.NotRegex(),
				new ToolEvals.ToolCalled(), new ToolEvals.ToolNotCalled(),
				new StructureEvals.Equals(), new StructureEvals.Length(), new StructureEvals.MaxToolCalls(),
				new JsonEvals.JsonValid(), new JsonEvals.JsonPath()));
		assertThat(registry.list()).hasSize(11);
		registry.validate(cfg("length", "max", 3), 1);
		assertThatThrownBy(() -> registry.validate(cfg("length", "max", "drei"), 1))
				.isInstanceOf(EntityInvalidException.class).hasMessageContaining("must be a number");
	}
}
