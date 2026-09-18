package com.devp.agent.agent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A staffed agent: model, identity and the tools it is allowed to use.
 *
 * <p>Until now a run composed provider, SOUL and tools ad hoc, and every agent could call
 * every registered tool. The agent makes that composition a versioned entity of its own —
 * the run records which agent in which revision worked, and the tool allowlist is the first
 * piece of tool governance.
 *
 * <p>What an agent is <em>not</em>: a strategy. How it learns is decided by the profile of a
 * training, never by the agent file. Keeping both apart is the point of the comparison —
 * the same agent trained under different profiles is the only measurement that means
 * anything.
 *
 * @param provider model provider id, {@code null} for the global default
 * @param soul     SOUL id, {@code null} for the active SOUL
 * @param tools    which of the registered tools this agent may call
 * @param sections further sections of the file, for humans
 * @param source   origin file, for provenance
 */
public record Agent(
		String id,
		String name,
		int version,
		String description,
		String provider,
		String soul,
		ToolAccess tools,
		Map<String, String> sections,
		String source) {

	/**
	 * Which tools an agent may call.
	 *
	 * <p>Three modes instead of a nullable list, because "all tools" and "no tool" are
	 * deliberate statements and must be readable as such in the file and in the UI.
	 * An allowlist naming a tool that is not registered is not an error: MCP servers come
	 * and go, and an agent must not become unusable because one of them is down.
	 */
	public record ToolAccess(Mode mode, List<String> names) {

		public enum Mode {

			/** Every registered tool. The state before governance existed. */
			ALL,

			/** No tool at all — for agents that are supposed to answer from the model alone. */
			NONE,

			/** Only the named tools. */
			ALLOWLIST
		}

		public static final ToolAccess ALL = new ToolAccess(Mode.ALL, List.of());

		public static final ToolAccess NONE = new ToolAccess(Mode.NONE, List.of());

		public static ToolAccess of(List<String> names) {
			Set<String> unique = new LinkedHashSet<>(names);
			return unique.isEmpty() ? NONE : new ToolAccess(Mode.ALLOWLIST, List.copyOf(unique));
		}

		public boolean allows(String toolName) {
			return switch (mode) {
				case ALL -> true;
				case NONE -> false;
				case ALLOWLIST -> names.contains(toolName);
			};
		}
	}
}
