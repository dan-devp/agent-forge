package com.devp.forge.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.devp.forge.doc.MarkdownDocument;
import com.devp.forge.store.EntityCodec;
import com.devp.forge.store.FileStore;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;

/**
 * <pre>
 * ---
 * id: forge
 * version: 2
 * name: Forge
 * model: gateway
 * tools: all          # or none, or [currentTime, divide]
 * ---
 * You are Forge, ...
 * </pre>
 */
public final class AgentCodec implements EntityCodec<Agent> {

	@Override
	public String kind() {
		return "agents";
	}

	@Override
	public Agent decode(MarkdownDocument d) {
		String prompt = d.body();
		if (prompt == null || prompt.isBlank()) {
			throw new EntityInvalidException("the body (system prompt) must not be empty");
		}
		return new Agent(
				d.id(),
				d.integer("version", 1),
				FileStore.required(d, "name"),
				d.text("description"),
				FileStore.required(d, "model"),
				tools(d),
				prompt);
	}

	/** Missing key means {@code all}, so a hand-written file without the line still works. */
	private static ToolAccess tools(MarkdownDocument d) {
		Object raw = d.frontMatter().get("tools");
		if (raw == null) {
			return ToolAccess.ALL;
		}
		if (raw instanceof List<?>) {
			return ToolAccess.of(d.strings("tools"));
		}
		String text = String.valueOf(raw).strip().toLowerCase();
		return switch (text) {
			case "all" -> ToolAccess.ALL;
			case "none", "" -> ToolAccess.NONE;
			default -> throw new EntityInvalidException("'tools' must be all, none or a list of tool names");
		};
	}

	@Override
	public Map<String, Object> frontMatter(Agent a) {
		Map<String, Object> fm = new LinkedHashMap<>();
		fm.put("id", a.id());
		fm.put("version", a.version());
		fm.put("name", a.name());
		if (a.description() != null && !a.description().isBlank()) {
			fm.put("description", a.description());
		}
		fm.put("model", a.modelId());
		fm.put("tools", a.tools().toFrontMatter());
		return fm;
	}

	@Override
	public String body(Agent a) {
		return a.systemPrompt();
	}

	@Override
	public Agent withVersion(Agent a, int version) {
		return new Agent(a.id(), version, a.name(), a.description(), a.modelId(), a.tools(), a.systemPrompt());
	}
}
