package com.devp.forge.eval;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.devp.forge.eval.EvalParam.Type;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evals for extraction tasks: is the answer JSON, and does a field hold the expected value?
 *
 * <p>Models wrap JSON in prose or code fences. The whole answer is tried first, then the
 * first fenced block, then the first {@code {...}} or {@code [...]} span — so a task does
 * not fail because the model said "Hier ist das JSON:" before delivering it.
 */
public final class JsonEvals {

	private JsonEvals() {
	}

	private static final JsonMapper MAPPER = JsonMapper.builder().build();
	private static final Pattern FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

	/** The first parseable JSON value in the text, or empty. */
	static Optional<JsonNode> extract(String text) {
		if (text == null || text.isBlank()) {
			return Optional.empty();
		}
		Optional<JsonNode> whole = tryParse(text.strip());
		if (whole.isPresent()) {
			return whole;
		}
		Matcher fence = FENCE.matcher(text);
		if (fence.find()) {
			Optional<JsonNode> fenced = tryParse(fence.group(1).strip());
			if (fenced.isPresent()) {
				return fenced;
			}
		}
		int obj = text.indexOf('{');
		int arr = text.indexOf('[');
		int start = obj < 0 ? arr : arr < 0 ? obj : Math.min(obj, arr);
		if (start < 0) {
			return Optional.empty();
		}
		char close = text.charAt(start) == '{' ? '}' : ']';
		int end = text.lastIndexOf(close);
		return end > start ? tryParse(text.substring(start, end + 1)) : Optional.empty();
	}

	private static Optional<JsonNode> tryParse(String candidate) {
		try {
			JsonNode node = MAPPER.readTree(candidate);
			return node == null || node.isMissingNode() || (!node.isObject() && !node.isArray())
					? Optional.empty()
					: Optional.of(node);
		}
		catch (RuntimeException ex) {
			return Optional.empty();
		}
	}

	/**
	 * Walks a dotted path with optional indices: {@code items[0].price}, {@code a.b}.
	 * Returns a missing node when any step is absent.
	 */
	static JsonNode at(JsonNode root, String path) {
		JsonNode node = root;
		for (String segment : path.split("\\.")) {
			if (segment.isBlank()) {
				continue;
			}
			Matcher m = Pattern.compile("([^\\[]*)((?:\\[\\d+])*)").matcher(segment);
			if (!m.matches()) {
				throw new EntityInvalidException("invalid path segment '" + segment + "'");
			}
			String key = m.group(1);
			if (!key.isEmpty()) {
				node = node.path(key);
			}
			Matcher idx = Pattern.compile("\\[(\\d+)]").matcher(m.group(2));
			while (idx.find()) {
				node = node.path(Integer.parseInt(idx.group(1)));
			}
			if (node.isMissingNode()) {
				return node;
			}
		}
		return node;
	}

	@Component
	public static class JsonValid implements Eval {
		@Override
		public String type() {
			return "json-valid";
		}

		@Override
		public String label() {
			return "Antwort ist JSON";
		}

		@Override
		public String description() {
			return "Besteht, wenn die Antwort ein JSON-Objekt oder -Array enthaelt (auch in einem Codeblock).";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(EvalParam.optional("strict", "Nur JSON, kein Text drumherum", Type.BOOLEAN, false,
					"Die ganze Antwort muss JSON sein, ohne Prosa oder Codeblock."));
		}

		@Override
		public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
			if (!subject.hasAnswer()) {
				return EvalResult.fail("Keine Antwort (Status " + subject.status() + ")");
			}
			if (config.bool("strict", false)) {
				return tryParse(subject.answer().strip()).isPresent()
						? EvalResult.pass("Antwort ist reines JSON")
						: EvalResult.fail("Antwort ist kein reines JSON");
			}
			return extract(subject.answer()).isPresent()
					? EvalResult.pass("Antwort enthaelt JSON")
					: EvalResult.fail("Kein JSON in der Antwort gefunden");
		}
	}

	@Component
	public static class JsonPath implements Eval {
		@Override
		public String type() {
			return "json-path";
		}

		@Override
		public String label() {
			return "JSON-Feld hat Wert";
		}

		@Override
		public String description() {
			return "Besteht, wenn das Feld unter dem Pfad existiert — und, falls angegeben, den erwarteten Wert hat.";
		}

		@Override
		public List<EvalParam> params() {
			return List.of(
					EvalParam.required("path", "Pfad", Type.STRING, "Punktpfad mit Indizes, z. B. items[0].price"),
					EvalParam.optional("value", "Erwarteter Wert", Type.STRING, null,
							"Als Text verglichen; leer = nur Existenz pruefen."));
		}

		@Override
		public EvalResult evaluate(EvalConfig config, EvalSubject subject) {
			String path = config.string("path");
			String expected = config.string("value");
			if (!subject.hasAnswer()) {
				return EvalResult.fail("Keine Antwort (Status " + subject.status() + ")");
			}
			Optional<JsonNode> root = extract(subject.answer());
			if (root.isEmpty()) {
				return EvalResult.fail("Kein JSON in der Antwort gefunden");
			}
			JsonNode node = at(root.get(), path);
			if (node.isMissingNode() || node.isNull()) {
				return EvalResult.fail("Pfad " + path + " fehlt");
			}
			if (expected == null || expected.isBlank()) {
				return EvalResult.pass(path + " = " + render(node));
			}
			String actual = node.isValueNode() ? node.asString() : node.toString();
			return actual.strip().equalsIgnoreCase(expected.strip())
					? EvalResult.pass(path + " = " + render(node))
					: EvalResult.fail(path + " = " + render(node) + ", erwartet " + expected);
		}

		private static String render(JsonNode node) {
			String text = node.isValueNode() ? node.asString() : node.toString();
			return text.length() <= 60 ? text : text.substring(0, 57) + "...";
		}
	}
}
