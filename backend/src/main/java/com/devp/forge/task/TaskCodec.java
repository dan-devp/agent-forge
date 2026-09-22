package com.devp.forge.task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.devp.forge.doc.MarkdownDocument;
import com.devp.forge.eval.EvalConfig;
import com.devp.forge.eval.EvalRegistry;
import com.devp.forge.store.EntityCodec;
import com.devp.forge.store.FileStore;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;

/**
 * <pre>
 * ---
 * id: time-and-division
 * version: 1
 * name: Zeit und Rechnung
 * max-tool-calls: 4
 * evals:
 *   - type: tool-called
 *     tool: currentTime
 *   - type: regex
 *     pattern: '\b14\b'
 * ---
 * Wie spaet ist es gerade in Berlin, und was ist 100 geteilt durch 7?
 * </pre>
 *
 * <p>Regexes go in single quotes: in double quotes YAML reads {@code \b} as a backspace.
 */
public final class TaskCodec implements EntityCodec<Task> {

	private final EvalRegistry evals;

	public TaskCodec(EvalRegistry evals) {
		this.evals = evals;
	}

	@Override
	public String kind() {
		return "tasks";
	}

	@Override
	public Task decode(MarkdownDocument d) {
		String input = d.body();
		if (input == null || input.isBlank()) {
			throw new EntityInvalidException("the body (task input) must not be empty");
		}
		Integer maxToolCalls = d.integer("max-tool-calls");
		if (maxToolCalls != null && maxToolCalls < 0) {
			throw new EntityInvalidException("'max-tool-calls' must not be negative");
		}

		List<EvalConfig> configs = new ArrayList<>();
		int index = 0;
		for (Map<?, ?> entry : d.objects("evals")) {
			index++;
			Object type = entry.get("type");
			if (type == null || String.valueOf(type).isBlank()) {
				throw new EntityInvalidException("eval #%d has no 'type'".formatted(index));
			}
			Map<String, Object> params = new LinkedHashMap<>();
			entry.forEach((k, v) -> {
				if (!"type".equals(String.valueOf(k))) {
					params.put(String.valueOf(k), v);
				}
			});
			EvalConfig config = new EvalConfig(String.valueOf(type).strip(), params);
			evals.validate(config, index);
			configs.add(config);
		}

		return new Task(
				d.id(),
				d.integer("version", 1),
				FileStore.required(d, "name"),
				d.text("description"),
				maxToolCalls,
				configs,
				input);
	}

	@Override
	public Map<String, Object> frontMatter(Task t) {
		Map<String, Object> fm = new LinkedHashMap<>();
		fm.put("id", t.id());
		fm.put("version", t.version());
		fm.put("name", t.name());
		if (t.description() != null && !t.description().isBlank()) {
			fm.put("description", t.description());
		}
		if (t.maxToolCalls() != null) {
			fm.put("max-tool-calls", t.maxToolCalls());
		}
		fm.put("evals", t.evals().stream().map(EvalConfig::toFrontMatter).toList());
		return fm;
	}

	@Override
	public String body(Task t) {
		return t.input();
	}

	@Override
	public Task withVersion(Task t, int version) {
		return new Task(t.id(), version, t.name(), t.description(), t.maxToolCalls(), t.evals(), t.input());
	}
}
