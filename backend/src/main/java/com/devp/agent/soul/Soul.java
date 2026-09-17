package com.devp.agent.soul;

import java.util.List;
import java.util.Map;

/**
 * Identity of an AI co-worker — self-contained and versioned, deliberately separated from
 * the task prompt.
 *
 * <p>A SOUL is more than prompt prose: the {@link SoulGate gates} operationalize the
 * personality as mandatory questions answered before every reply. Prose describes the
 * stance, gates enforce it.
 *
 * @param sections heading -&gt; text, in file order. Deliberately untyped: which sections a
 *                 SOUL carries is up to the SOUL.
 * @param source   origin of the definition, for provenance in answers and logs.
 */
public record Soul(
		String id,
		String name,
		String role,
		int version,
		String description,
		List<SoulGate> gates,
		Map<String, String> sections,
		String source) {

	/** Display name with role, e.g. for logs and the frontend. */
	public String displayName() {
		return role == null || role.isBlank() ? name : "%s (%s)".formatted(name, role);
	}
}
