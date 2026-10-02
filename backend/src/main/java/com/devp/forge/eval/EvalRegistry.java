package com.devp.forge.eval;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.devp.forge.store.StoreExceptions.EntityInvalidException;

import org.springframework.stereotype.Component;

/**
 * All eval types the system provides, by type key.
 *
 * <p>Spring collects every {@link Eval} bean; adding an eval is one class. The registry is
 * also the validator: a task naming an unknown type or missing a required parameter is
 * rejected when saved, not discovered when the first run is graded.
 */
@Component
public class EvalRegistry {

	private final Map<String, Eval> evals;

	public EvalRegistry(List<Eval> evals) {
		Map<String, Eval> byType = new LinkedHashMap<>();
		for (Eval eval : evals) {
			Eval previous = byType.put(eval.type(), eval);
			if (previous != null) {
				throw new IllegalStateException("Two evals share the type '" + eval.type() + "'");
			}
		}
		this.evals = Collections.unmodifiableMap(byType);
	}

	public List<Eval> list() {
		return List.copyOf(evals.values());
	}

	public Eval get(String type) {
		Eval eval = evals.get(type);
		if (eval == null) {
			throw new EntityInvalidException(
					"unknown eval type '%s' — known: %s".formatted(type, evals.keySet()));
		}
		return eval;
	}

	/** @param index 1-based position in the task, for the message */
	public void validate(EvalConfig config, int index) {
		Eval eval;
		try {
			eval = get(config.type());
		}
		catch (EntityInvalidException ex) {
			throw new EntityInvalidException("eval #%d: %s".formatted(index, ex.getMessage()));
		}
		for (EvalParam param : eval.params()) {
			Object value = config.params().get(param.name());
			boolean blank = value == null || String.valueOf(value).isBlank()
					|| (param.type() == EvalParam.Type.LIST && config.strings(param.name()).isEmpty());
			if (param.required() && blank) {
				throw new EntityInvalidException(
						"eval #%d (%s): '%s' is required".formatted(index, eval.type(), param.name()));
			}
			if (!blank && param.type() == EvalParam.Type.NUMBER && !(value instanceof Number)) {
				try {
					Double.parseDouble(String.valueOf(value));
				}
				catch (NumberFormatException ex) {
					throw new EntityInvalidException(
							"eval #%d (%s): '%s' must be a number".formatted(index, eval.type(), param.name()));
				}
			}
		}
		List<String> known = eval.params().stream().map(EvalParam::name).toList();
		for (String key : config.params().keySet()) {
			if (!known.contains(key)) {
				throw new EntityInvalidException(
						"eval #%d (%s): unknown parameter '%s' — known: %s".formatted(index, eval.type(), key, known));
			}
		}
	}
}
