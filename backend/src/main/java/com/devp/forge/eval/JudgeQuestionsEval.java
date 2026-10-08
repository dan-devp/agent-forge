package com.devp.forge.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.devp.forge.ai.ChatClientRegistry;
import com.devp.forge.ai.DecisionClientRegistry;
import com.devp.forge.ai.DecisionClientRegistry.Decisions;
import com.devp.forge.eval.EvalParam.Type;
import com.devp.forge.model.Model;
import com.devp.forge.model.ModelKind;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * A judge answers a list of yes/no questions about the answer; every question is its own
 * verdict, so the run series shows which one fails and how often.
 *
 * <p>Passing is a conjunction anyway, and an atomic question needs no prose to explain a
 * failure: the question that said no <em>is</em> the reason. Any model that can judge may
 * answer. A decision model (TypeSafe) takes all questions in one call and returns a truth
 * value per question, cut at the threshold. A chat model gets the questions numbered and
 * answers YES or NO per line with a short reason (JA and NEIN are understood as well).
 *
 * <p>Like {@link LlmJudgeEval}, the judge sees input and answer only. The cost of the one
 * call is written to the first verdict, so summing verdicts gives the true judge cost.
 */
@Component
public class JudgeQuestionsEval implements Eval {

	private static final Logger log = LoggerFactory.getLogger(JudgeQuestionsEval.class);

	private static final double DEFAULT_THRESHOLD = 0.5;

	private static final String SYSTEM = """
			You are a strict examiner. You get a task, an agent's answer and numbered yes/no \
			questions about that answer. Answer each question on its own.

			Answer with exactly one line per question, in this form:
			1: YES - one short sentence
			2: NO - one short sentence""";

	private static final Pattern LINE = Pattern.compile("^\\s*(\\d+)\\s*[:.)]\\s*(YES|NO|JA|NEIN)\\b\\s*[-:]?\\s*(.*)$",
			Pattern.CASE_INSENSITIVE);

	/** The answers that mean yes: the prompt asks for YES, older answers said JA. */
	private static final java.util.Set<String> YES = java.util.Set.of("YES", "JA");

	private final ChatClientRegistry chat;
	private final DecisionClientRegistry decisions;

	public JudgeQuestionsEval(ChatClientRegistry chat, DecisionClientRegistry decisions) {
		this.chat = chat;
		this.decisions = decisions;
	}

	@Override
	public String type() {
		return "judge-questions";
	}

	@Override
	public String label() {
		return "Yes/no questions to a judge";
	}

	@Override
	public String description() {
		return "A judge answers each question with yes or no; every question is its own verdict. "
				+ "Works with chat models and with decision models (TypeSafe) - those answer all questions in one call.";
	}

	@Override
	public List<EvalParam> params() {
		return List.of(
				EvalParam.required("model", "Judge model", Type.JUDGE_MODEL, "Chat or decision model."),
				EvalParam.required("questions", "Questions", Type.LIST,
						"One question per line, asked so that yes means passed."),
				EvalParam.optional("threshold", "Threshold", Type.NUMBER, DEFAULT_THRESHOLD,
						"From this value (0 to 1) a decision model counts as yes. Chat models ignore it."));
	}

	/** Never called on its own: this eval always answers per question. */
	@Override
	public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
		List<EvalResult> all = evaluateAll(config, subject);
		return all.stream().filter(r -> !r.passed()).findFirst().orElse(all.getFirst());
	}

	@Override
	public List<EvalResult> evaluateAll(EvalConfig config, EvalSubject subject) {
		List<String> questions = config.strings("questions");
		if (questions.isEmpty()) {
			return List.of(EvalResult.fail("No questions configured"));
		}
		if (!subject.hasAnswer()) {
			return each(questions, EvalResult.fail("No answer (status " + subject.status() + ")"));
		}
		Model model;
		try {
			model = chat.model(config.string("model"));
		}
		catch (RuntimeException ex) {
			return each(questions, EvalResult.fail("Judge model not available: " + ex.getMessage()));
		}
		return model.kind() == ModelKind.DECISION
				? byDecision(model, questions, config.number("threshold", DEFAULT_THRESHOLD), subject)
				: byChat(model, questions, subject);
	}

	private List<EvalResult> byDecision(Model model, List<String> questions, double threshold, EvalSubject subject) {
		Map<String, String> keyed = new LinkedHashMap<>();
		for (int i = 0; i < questions.size(); i++) {
			keyed.put("q" + (i + 1), questions.get(i));
		}
		Map<String, String> state = new LinkedHashMap<>();
		state.put("task", subject.input());
		state.put("answer", subject.answer());

		long start = System.currentTimeMillis();
		Decisions answer;
		try {
			answer = decisions.ask(model, state, keyed);
		}
		catch (RuntimeException ex) {
			log.warn("Decision judge '{}' failed: {}", model.id(), ex.getMessage());
			return failedCall(model, questions, ex, System.currentTimeMillis() - start);
		}
		long duration = System.currentTimeMillis() - start;

		List<EvalResult> results = new ArrayList<>();
		int i = 0;
		for (Map.Entry<String, String> q : keyed.entrySet()) {
			double value = answer.values().get(q.getKey());
			boolean yes = value >= threshold;
			// Locale.ROOT: the rationale is stored, and 0,93 vs 0.93 must not depend on the host.
			String rationale = String.format(Locale.ROOT, "%s (%.2f%s)", yes ? "Yes" : "No", value,
					yes ? "" : String.format(Locale.ROOT, " < %.2f", threshold));
			results.add(costOnFirst(i++, yes, rationale, answer.model(), answer.tokens(), duration)
					.about(q.getValue()));
		}
		return results;
	}

	private List<EvalResult> byChat(Model model, List<String> questions, EvalSubject subject) {
		StringBuilder numbered = new StringBuilder();
		for (int i = 0; i < questions.size(); i++) {
			numbered.append(i + 1).append(". ").append(questions.get(i)).append('\n');
		}
		String user = """
				# Task
				%s

				# The agent's answer
				%s

				# Questions
				%s""".formatted(subject.input(), subject.answer(), numbered.toString().strip());

		long start = System.currentTimeMillis();
		ChatResponse response;
		try {
			response = chat.client(model).prompt().system(SYSTEM).user(user).call().chatResponse();
		}
		catch (RuntimeException ex) {
			log.warn("Chat judge '{}' failed: {}", model.id(), ex.getMessage());
			return failedCall(model, questions, ex, System.currentTimeMillis() - start);
		}
		long duration = System.currentTimeMillis() - start;

		String text = response == null || response.getResult() == null ? "" : response.getResult().getOutput().getText();
		Integer tokens = response == null || response.getMetadata().getUsage() == null
				? null
				: response.getMetadata().getUsage().getTotalTokens();
		Map<Integer, Line> lines = parse(text == null ? "" : text);

		List<EvalResult> results = new ArrayList<>();
		for (int i = 0; i < questions.size(); i++) {
			Line line = lines.get(i + 1);
			boolean yes = line != null && line.yes();
			String rationale = line == null
					? "No answer to question " + (i + 1) + " in the judge's text"
					: (yes ? "Yes" : "No") + (line.reason().isEmpty() ? "" : ": " + line.reason());
			results.add(costOnFirst(i, yes, rationale, model.modelId(), tokens, duration).about(questions.get(i)));
		}
		return results;
	}

	/** One line per question number; the first answer to a number wins, anything unparseable is ignored. */
	static Map<Integer, Line> parse(String text) {
		Map<Integer, Line> lines = new LinkedHashMap<>();
		for (String raw : text.split("\n")) {
			Matcher m = LINE.matcher(raw);
			if (m.matches()) {
				lines.putIfAbsent(Integer.parseInt(m.group(1)),
						new Line(YES.contains(m.group(2).toUpperCase(Locale.ROOT)), m.group(3).strip()));
			}
		}
		return lines;
	}

	private static EvalResult costOnFirst(int index, boolean passed, String rationale, String model, Integer tokens,
			long duration) {
		return index == 0
				? new EvalResult(passed, rationale, model, tokens, duration)
				: new EvalResult(passed, rationale, model, null, null);
	}

	private static List<EvalResult> failedCall(Model model, List<String> questions, RuntimeException ex,
			long duration) {
		List<EvalResult> results = new ArrayList<>();
		for (int i = 0; i < questions.size(); i++) {
			results.add(costOnFirst(i, false, "Judge call failed: " + ex.getMessage(), model.modelId(), null,
					duration).about(questions.get(i)));
		}
		return results;
	}

	private static List<EvalResult> each(List<String> questions, EvalResult result) {
		return questions.stream().map(result::about).toList();
	}

	record Line(boolean yes, String reason) {
	}
}
