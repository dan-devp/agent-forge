package com.devp.forge.eval;

/**
 * One configurable parameter of an {@link Eval}.
 *
 * @param name         key in the task file
 * @param label        shown in the UI (German)
 * @param type         how the UI renders it and how the registry checks it
 * @param required     validation fails without it
 * @param defaultValue used when absent; {@code null} for none
 */
public record EvalParam(String name, String label, Type type, boolean required, Object defaultValue,
		String description) {

	public enum Type {
		/** One line of text. */
		STRING,
		/** Several lines of text. */
		TEXT,
		NUMBER,
		BOOLEAN,
		/** Id of a {@link com.devp.forge.model.Model}. */
		MODEL,
		/** Name of a registered tool. */
		TOOL
	}

	public static EvalParam required(String name, String label, Type type, String description) {
		return new EvalParam(name, label, type, true, null, description);
	}

	public static EvalParam optional(String name, String label, Type type, Object defaultValue, String description) {
		return new EvalParam(name, label, type, false, defaultValue, description);
	}
}
