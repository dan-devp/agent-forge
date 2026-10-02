package com.devp.forge.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.devp.forge.ai.ChatClientRegistry;
import com.devp.forge.ai.DecisionClientRegistry;
import com.devp.forge.ai.DecisionClientRegistry.Decisions;
import com.devp.forge.model.Model;
import com.devp.forge.model.Provider;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The question judge with both registries mocked: no endpoint is ever reached. */
class JudgeQuestionsEvalTests {

	private static final Model JEV = new Model("jev", 1, "Jev", null, Provider.TYPESAFE, "https://api.typesafe.ai/v1",
			"jev-latest", "API_KEY_TYPESAFE", 0.7);

	private static final EvalSubject ANSWERED = new EvalSubject("Wie spaet ist es?", "Es ist 10:00 Uhr.", "DONE",
			List.of());

	private final ChatClientRegistry chat = mock(ChatClientRegistry.class);
	private final DecisionClientRegistry decisions = mock(DecisionClientRegistry.class);
	private final JudgeQuestionsEval eval = new JudgeQuestionsEval(chat, decisions);

	private static EvalConfig cfg(Object questions, Object threshold) {
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("model", "jev");
		params.put("questions", questions);
		if (threshold != null) {
			params.put("threshold", threshold);
		}
		return new EvalConfig("judge-questions", params);
	}

	@Test
	void decisionModelGivesOneVerdictPerQuestionCutAtTheThreshold() {
		when(chat.model("jev")).thenReturn(JEV);
		when(decisions.ask(eq(JEV), any(), any())).thenReturn(
				new Decisions(Map.of("q1", 0.93, "q2", 0.41, "q3", 0.6), "jev-1.13.0", 57));

		List<EvalResult> results = eval.evaluateAll(
				cfg(List.of("Nennt die Uhrzeit?", "Nennt die Zeitzone?", "Ist sie knapp?"), 0.55), ANSWERED);

		assertThat(results).extracting(EvalResult::subject)
				.containsExactly("Nennt die Uhrzeit?", "Nennt die Zeitzone?", "Ist sie knapp?");
		assertThat(results).extracting(EvalResult::passed).containsExactly(true, false, true);
		assertThat(results.get(0).rationale()).isEqualTo("Ja (0.93)");
		assertThat(results.get(1).rationale()).isEqualTo("Nein (0.41 < 0.55)");
		// One call, its cost once: summing verdicts must give the real judge cost.
		assertThat(results).extracting(EvalResult::tokens).containsExactly(57, null, null);
		assertThat(results).extracting(EvalResult::model).containsOnly("jev-1.13.0");
	}

	@Test
	void noAnswerFailsEveryQuestionWithoutCallingTheJudge() {
		EvalSubject silent = new EvalSubject("Frage", null, "BUDGET_EXCEEDED", List.of());

		List<EvalResult> results = eval.evaluateAll(cfg(List.of("A?", "B?"), null), silent);

		assertThat(results).hasSize(2).noneMatch(EvalResult::passed);
		assertThat(results).extracting(EvalResult::subject).containsExactly("A?", "B?");
		verify(decisions, never()).ask(any(), any(), any());
	}

	@Test
	void aFailedCallFailsEveryQuestionAndSaysWhy() {
		when(chat.model("jev")).thenReturn(JEV);
		when(decisions.ask(eq(JEV), any(), any())).thenThrow(new IllegalStateException("401"));

		List<EvalResult> results = eval.evaluateAll(cfg(List.of("A?", "B?"), null), ANSWERED);

		assertThat(results).noneMatch(EvalResult::passed);
		assertThat(results.getFirst().rationale()).contains("Judge-Aufruf fehlgeschlagen").contains("401");
	}

	@Test
	void chatAnswersAreReadPerNumberAndAMissingOneFails() {
		Map<Integer, JudgeQuestionsEval.Line> lines = JudgeQuestionsEval.parse("""
				1: JA - die Uhrzeit steht da
				2) nein: keine Zeitzone
				3. Ja
				1: NEIN - spaetere Zeile zaehlt nicht
				Fazit: passt""");

		assertThat(lines).containsOnlyKeys(1, 2, 3);
		assertThat(lines.get(1).yes()).isTrue();
		assertThat(lines.get(1).reason()).isEqualTo("die Uhrzeit steht da");
		assertThat(lines.get(2).yes()).isFalse();
		assertThat(lines.get(3).reason()).isEmpty();
	}

	@Test
	void registryRequiresAtLeastOneQuestion() {
		EvalRegistry registry = new EvalRegistry(List.of(eval));

		assertThatThrownBy(() -> registry.validate(cfg(List.of(), null), 1))
				.isInstanceOf(EntityInvalidException.class).hasMessageContaining("'questions' is required");
		assertThatThrownBy(() -> registry.validate(cfg(List.of("  "), null), 1))
				.isInstanceOf(EntityInvalidException.class);
		registry.validate(cfg(List.of("A?"), 0.7), 1);
	}
}
