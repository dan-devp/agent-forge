package com.devp.forge.eval;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One eval as configured in a task: which {@link Eval} type, with which parameters.
 *
 * <p>The type is code, the parameters are the task author's — a regex, a judge's model, an
 * instruction. Stored in the task file as one YAML object per entry under {@code evals:}.
 */
public record EvalConfig(String type, Map<String, Object> params) {

	public EvalConfig {
		// Not Map.copyOf: the parameter order is the order the file shows.
		params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
	}

	public String string(String name) {
		Object value = params.get(name);
		return value == null ? null : String.valueOf(value);
	}

	public String string(String name, String fallback) {
		String value = string(name);
		return value == null || value.isBlank() ? fallback : value;
	}

	public boolean bool(String name, boolean fallback) {
		Object value = params.get(name);
		if (value == null) {
			return fallback;
		}
		return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
	}

	/** The front matter form: {@code type} first, then the parameters in their order. */
	public Map<String, Object> toFrontMatter() {
		Map<String, Object> fm = new LinkedHashMap<>();
		fm.put("type", type);
		fm.putAll(params);
		return fm;
	}
}
