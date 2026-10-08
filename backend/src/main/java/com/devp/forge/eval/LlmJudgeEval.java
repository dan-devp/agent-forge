package com.devp.forge.eval;

import java.util.List;
import java.util.Locale;

import com.devp.forge.ai.ChatClientRegistry;
import com.devp.forge.eval.EvalParam.Type;
import com.devp.forge.model.Model;
import com.devp.forge.model.ModelKind;

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
			You are a strict examiner. You get a task, an agent's answer and a criterion. \
			Decide whether the answer meets the criterion.

			Answer in exactly two lines:
			VERDICT: PASS or FAIL
			REASON: one sentence""";

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
		return "Verdict by a judge model";
	}

	@Override
	public String description() {
		return "A model checks the answer against a criterion in plain words. Costs one model call.";
	}

	@Override
	public List<EvalParam> params() {
		return List.of(
				EvalParam.required("model", "Judge model", Type.MODEL, "Which model judges."),
				EvalParam.required("criterion", "Criterion", Type.TEXT,
						"What the answer has to achieve, in one or two sentences."));
	}

	@Override
	public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
		if (!subject.hasAnswer()) {
			return EvalResult.fail("No answer (status " + subject.status() + ")");
		}
		Model model;
		try {
			model = clients.model(config.string("model"));
		}
		catch (RuntimeException ex) {
			return EvalResult.fail("Judge model not available: " + ex.getMessage());
		}
		if (model.kind() != ModelKind.CHAT) {
			return EvalResult.fail("'" + model.id() + "' is a decision model and writes no text - "
					+ "use judge-questions for it");
		}

		String user = """
				# Task
				%s

				# The agent's answer
				%s

				# Criterion
				%s""".formatted(subject.input(), subject.answer(), config.string("criterion"));

		long start = System.currentTimeMillis();
		ChatResponse response;
		try {
			response = clients.client(model).prompt().system(SYSTEM).user(user).call().chatResponse();
		}
		catch (RuntimeException ex) {
			log.warn("Judge '{}' failed: {}", model.id(), ex.getMessage());
			return new EvalResult(false, "Judge call failed: " + ex.getMessage(), model.modelId(), null,
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

	/**
	 * Lenient on shape, strict on content: PASS must be there, and FAIL must not. The German
	 * labels of the earlier prompt are still understood.
	 */
	static Verdict parse(String text) {
		String rationale = null;
		Boolean passed = null;
		for (String line : text.strip().split("\n")) {
			String upper = line.toUpperCase(Locale.ROOT);
			if (upper.startsWith("VERDICT") || upper.startsWith("URTEIL")) {
				passed = upper.contains("PASS") && !upper.contains("FAIL");
			}
			else if (upper.startsWith("REASON") || upper.startsWith("BEGRUENDUNG") || upper.startsWith("BEGR\u00dcNDUNG")) {
				int colon = line.indexOf(':');
				rationale = colon < 0 ? line : line.substring(colon + 1).strip();
			}
		}
		if (passed == null) {
			String upper = text.toUpperCase(Locale.ROOT);
			passed = upper.contains("PASS") && !upper.contains("FAIL");
			rationale = "Unclear format, raw text: " + text.strip();
		}
		return new Verdict(passed, rationale == null ? "(no reason given)" : rationale);
	}

	record Verdict(boolean passed, String rationale) {
	}
}
