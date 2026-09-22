package com.devp.forge.agent;

import com.devp.forge.store.Versioned;

/**
 * Who works: a model and a system prompt, plus which tools may be offered.
 *
 * <p>The prompt is the file's body, so the agent reads like a document — front matter
 * says what runs it, the prose says who it is.
 *
 * @param modelId id of the {@link com.devp.forge.model.Model}; the current version is used at run time
 */
public record Agent(
		String id,
		int version,
		String name,
		String description,
		String modelId,
		ToolAccess tools,
		String systemPrompt) implements Versioned {
}
