package com.devp.agent.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.devp.agent.profile.Profile;
import com.devp.agent.soul.Soul;
import com.devp.agent.task.Task;

import org.springframework.ai.tool.ToolCallback;

/**
 * What a strategy may know about and contribute to the run in progress.
 *
 * <p>Deliberately narrow: strategies append to the system prompt and offer additional tools.
 * They do not replace the SOUL, do not see the provider and do not touch the database —
 * whatever they learn must go through the hooks, so it stays visible in the trace.
 *
 * @param task    {@code null} for a free-form goal
 * @param profile the profile that activated the strategies
 */
public final class RunContext {

	private final UUID runId;
	private final String goal;
	private final Task task;
	private final Soul soul;
	private final Profile profile;
	private final List<String> promptAdditions = new ArrayList<>();
	private final List<ToolCallback> extraTools = new ArrayList<>();

	public RunContext(UUID runId, String goal, Task task, Soul soul, Profile profile) {
		this.runId = runId;
		this.goal = goal;
		this.task = task;
		this.soul = soul;
		this.profile = profile;
	}

	public UUID runId() {
		return runId;
	}

	public String goal() {
		return goal;
	}

	public Task task() {
		return task;
	}

	public Soul soul() {
		return soul;
	}

	public Profile profile() {
		return profile;
	}

	/** Text appended to the rendered SOUL prompt, e.g. retrieved lessons or a playbook. */
	public void addToPrompt(String text) {
		if (text != null && !text.isBlank()) {
			promptAdditions.add(text.strip());
		}
	}

	public List<String> promptAdditions() {
		return List.copyOf(promptAdditions);
	}

	/** A tool offered in addition to the registry's, e.g. a retrieval tool for proactive lookup. */
	public void addTool(ToolCallback tool) {
		extraTools.add(tool);
	}

	public List<ToolCallback> extraTools() {
		return List.copyOf(extraTools);
	}
}
