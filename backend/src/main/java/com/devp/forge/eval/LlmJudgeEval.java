package com.devp.forge.eval;

import java.util.List;
import java.util.Locale;

import com.devp.forge.ai.ChatClientRegistry;
import com.devp.forge.eval.EvalParam.Type;
import com.devp.forge.model.Model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * A model judges the answer against an instruction and says PASS or FAIL.
 *
 * <p>The judge is its own model entity, chosen per task — so the same model as the agent
 * is a decision, not an accident. The judge sees input and answer only, never the system
 * prompt or trace: it grades the result, not the process. Its tokens and time land on the
 * verdict, because a judge that costs nothing on paper skews every cost comparison.
 */
@Component
public class LlmJudgeEval implements Eval {

	private static final Logger log = LoggerFactory.getLogger(LlmJudgeEval.class);

	private static final String SYSTEM = """
			Du bist ein strenger Pruefer. Du bekommst eine Aufgabe, die Antwort eines Agenten \
			und ein Kriterium. Entscheide, ob die Antwort das Kriterium erfuellt.

			Antworte in genau zwei Zeilen:
			URTEIL: PASS oder FAIL
			BEGRUENDUNG: ein Satz""";

	private final ChatClientRegistry clients;

	public LlmJudgeEval(ChatClientRegistry clients) {
		this.clients = clients;
	}

	@Override
	public String type() {
		return "llm-judge";
	}

	@Override
	public String label() {
		return "Urteil durch Judge-Model";
	}

	@Override
	public String description() {
		return "Ein Model prueft die Antwort gegen ein frei formuliertes Kriterium. Kostet einen Model-Aufruf.";
	}

	@Override
	public List<EvalParam> params() {
		return List.of(
				EvalParam.required("model", "Judge-Model", Type.MODEL, "Welches Model urteilt."),
				EvalParam.required("criterion", "Kriterium", Type.TEXT,
						"Was die Antwort leisten muss, in einem oder zwei Saetzen."));
	}

	@Override
	public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
		if (!subject.hasAnswer()) {
			return EvalResult.fail("Keine Antwort (Status " + subject.status() + ")");
		}
		Model model;
		try {
			model = clients.model(config.string("model"));
		}
		catch (RuntimeException ex) {
			return EvalResult.fail("Judge-Model nicht verfuegbar: " + ex.getMessage());
		}

		String user = """
				# Aufgabe
				%s

				# Antwort des Agenten
				%s

				# Kriterium
				%s""".formatted(subject.input(), subject.answer(), config.string("criterion"));

		long start = System.currentTimeMillis();
		ChatResponse response;
		try {
			response = clients.client(model).prompt().system(SYSTEM).user(user).call().chatResponse();
		}
		catch (RuntimeException ex) {
			log.warn("Judge '{}' failed: {}", model.id(), ex.getMessage());
			return new EvalResult(false, "Judge-Aufruf fehlgeschlagen: " + ex.getMessage(), model.modelId(), null,
					System.currentTimeMillis() - start);
		}
		long duration = System.currentTimeMillis() - start;

		String text = response == null || response.getResult() == null ? "" : response.getResult().getOutput().getText();
		Integer tokens = response == null || response.getMetadata().getUsage() == null
				? null
				: response.getMetadata().getUsage().getTotalTokens();

		Verdict verdict = parse(text == null ? "" : text);
		return new EvalResult(verdict.passed(), verdict.rationale(), model.modelId(), tokens, duration);
	}

	/** Lenient on shape, strict on content: PASS must be there, and FAIL must not. */
	static Verdict parse(String text) {
		String rationale = null;
		Boolean passed = null;
		for (String line : text.strip().split("\n")) {
			String upper = line.toUpperCase(Locale.ROOT);
			if (upper.startsWith("URTEIL")) {
				passed = upper.contains("PASS") && !upper.contains("FAIL");
			}
			else if (upper.startsWith("BEGRUENDUNG") || upper.startsWith("BEGRÜNDUNG")) {
				int colon = line.indexOf(':');
				rationale = colon < 0 ? line : line.substring(colon + 1).strip();
			}
		}
		if (passed == null) {
			String upper = text.toUpperCase(Locale.ROOT);
			passed = upper.contains("PASS") && !upper.contains("FAIL");
			rationale = "Unklares Format, Rohtext: " + text.strip();
		}
		return new Verdict(passed, rationale == null ? "(keine Begruendung)" : rationale);
	}

	record Verdict(boolean passed, String rationale) {
	}
}
