package com.devp.forge.agent;

import java.util.List;
import java.util.Set;

/**
 * Which registered tools an agent may be offered.
 *
 * <p>{@code all} and {@code none} are the two ends; a list in between is a positive list —
 * a tool not named is not offered. Filtering happens on what the model sees, not on what it
 * calls: a tool the model never hears of cannot be misused.
 */
public sealed interface ToolAccess {

	boolean allows(String toolName);

	/** Front matter form: {@code all}, {@code none} or the list. */
	Object toFrontMatter();

	record All() implements ToolAccess {
		@Override
		public boolean allows(String toolName) {
			return true;
		}

		@Override
		public Object toFrontMatter() {
			return "all";
		}
	}

	record None() implements ToolAccess {
		@Override
		public boolean allows(String toolName) {
			return false;
		}

		@Override
		public Object toFrontMatter() {
			return "none";
		}
	}

	record Only(List<String> names) implements ToolAccess {
		public Only {
			names = List.copyOf(names);
		}

		@Override
		public boolean allows(String toolName) {
			return names.contains(toolName);
		}

		@Override
		public Object toFrontMatter() {
			return names;
		}
	}

	ToolAccess ALL = new All();
	ToolAccess NONE = new None();

	static ToolAccess of(List<String> names) {
		return names.isEmpty() ? NONE : new Only(List.copyOf(Set.copyOf(names).stream().sorted().toList()));
	}
}
