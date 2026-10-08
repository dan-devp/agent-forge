package com.devp.forge.eval;

import java.util.List;
import java.util.Map;

import com.devp.forge.store.StoreExceptions.EntityInvalidException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The deterministic evals and the registry's validation, no Spring involved. */
class EvalTests {

	private static final EvalSubject ANSWERED = new EvalSubject("Question", "It is 10:00 am, 100/7 = 14 remainder 2.",
			"DONE", List.of("currentTime", "divide", "divide"));

	private static final EvalSubject SILENT = new EvalSubject("Question", null, "BUDGET_EXCEEDED", List.of("divide"));

	private static EvalConfig cfg(String type, Object... kv) {
		var params = new java.util.LinkedHashMap<String, Object>();
		for (int i = 0; i < kv.length; i += 2) {
			params.put((String) kv[i], kv[i + 1]);
		}
		return new EvalConfig(type, params);
	}

	@Test
	void containsIgnoresCaseByDefault() {
		Eval eval = new TextEvals.Contains();
		assertThat(eval.evaluate(cfg("contains", "text", "REMAINDER 2"), ANSWERED).passed()).isTrue();
		assertThat(eval.evaluate(cfg("contains", "text", "REMAINDER 2", "ignore-case", false), ANSWERED).passed())
				.isFalse();
		assertThat(eval.evaluate(cfg("contains", "text", "am"), SILENT).passed()).isFalse();
	}

	@Test
	void notContainsFailsWithoutAnswerToo() {
		Eval eval = new TextEvals.NotContains();
		assertThat(eval.evaluate(cfg("not-contains", "text", "error"), ANSWERED).passed()).isTrue();
		assertThat(eval.evaluate(cfg("not-contains", "text", "error"), SILENT).passed()).isFalse();
	}

	@Test
	void regexFindsAnywhereAndReportsTheMatch() {
		EvalResult result = new TextEvals.Regex().evaluate(cfg("regex", "pattern", "\\b14\\b"), ANSWERED);
		assertThat(result.passed()).isTrue();
		assertThat(result.rationale()).contains("14");
		assertThat(new TextEvals.Regex().evaluate(cfg("regex", "pattern", "\\b15\\b"), ANSWERED).passed()).isFalse();
	}

	@Test
	void regexRejectsBrokenPatterns() {
		assertThatThrownBy(() -> new TextEvals.Regex().evaluate(cfg("regex", "pattern", "("), ANSWERED))
				.isInstanceOf(EntityInvalidException.class);
	}

	@Test
	void toolCalledCountsRepeats() {
		EvalResult result = new ToolEvals.ToolCalled().evaluate(cfg("tool-called", "tool", "divide"), ANSWERED);
		assertThat(result.passed()).isTrue();
		assertThat(result.rationale()).contains("2x");
		assertThat(new ToolEvals.ToolCalled().evaluate(cfg("tool-called", "tool", "search"), ANSWERED).passed())
				.isFalse();
	}

	@Test
	void toolNotCalledWithStarMeansNone() {
		Eval eval = new ToolEvals.ToolNotCalled();
		assertThat(eval.evaluate(cfg("tool-not-called", "tool", "*"), ANSWERED).passed()).isFalse();
		assertThat(eval.evaluate(cfg("tool-not-called", "tool", "*"),
				new EvalSubject("F", "A", "DONE", List.of())).passed()).isTrue();
		assertThat(eval.evaluate(cfg("tool-not-called", "tool", "search"), ANSWERED).passed()).isTrue();
	}

	@Test
	void judgeVerdictParsingIsLenientOnShapeStrictOnContent() {
		assertThat(LlmJudgeEval.parse("VERDICT: PASS\nREASON: fits.").passed()).isTrue();
		assertThat(LlmJudgeEval.parse("VERDICT: PASS\nREASON: fits.").rationale()).isEqualTo("fits.");
		assertThat(LlmJudgeEval.parse("Verdict: FAIL\nReason: remainder missing").passed()).isFalse();
		// The German labels of the earlier prompt still read.
		assertThat(LlmJudgeEval.parse("URTEIL: PASS\nBEGRUENDUNG: passt.").rationale()).isEqualTo("passt.");
		assertThat(LlmJudgeEval.parse("Urteil: FAIL\nBegr\u00fcndung: fehlt Rest").passed()).isFalse();
		assertThat(LlmJudgeEval.parse("PASS").passed()).isTrue();
		assertThat(LlmJudgeEval.parse("PASS or FAIL, hard to say").passed()).isFalse();
		assertThat(LlmJudgeEval.parse("").passed()).isFalse();
	}

	@Test
	void registryValidatesTypeAndParameters() {
		EvalRegistry registry = new EvalRegistry(List.of(new TextEvals.Regex(), new ToolEvals.ToolCalled()));

		registry.validate(cfg("regex", "pattern", "x"), 1);

		assertThatThrownBy(() -> registry.validate(cfg("nope"), 1))
				.isInstanceOf(EntityInvalidException.class).hasMessageContaining("unknown eval type");
		assertThatThrownBy(() -> registry.validate(cfg("regex"), 2))
				.isInstanceOf(EntityInvalidException.class).hasMessageContaining("'pattern' is required");
		assertThatThrownBy(() -> registry.validate(cfg("tool-called", "tool", "x", "extra", 1), 3))
				.isInstanceOf(EntityInvalidException.class).hasMessageContaining("unknown parameter 'extra'");
	}

	@Test
	void registryRefusesDuplicateTypes() {
		assertThatThrownBy(() -> new EvalRegistry(List.of(new TextEvals.Regex(), new TextEvals.Regex())))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void configKeepsParameterOrder() {
		EvalConfig config = new EvalConfig("llm-judge", Map.of());
		assertThat(config.toFrontMatter().keySet()).containsExactly("type");
		EvalConfig ordered = cfg("x", "b", 1, "a", 2);
		assertThat(ordered.toFrontMatter().keySet()).containsExactly("type", "b", "a");
	}
}
