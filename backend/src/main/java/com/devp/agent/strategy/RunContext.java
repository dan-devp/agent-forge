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
 * <p>Deliberately narrow: strategies append to the system prompt, offer additional tools and
 * remember things in the training's {@link MemoryStore}. They do not replace the SOUL, do not
 * see the provider and do not touch the database directly — whatever they learn goes through
 * the store, attributed to the run, so a learning curve stays explainable.
 *
 * @param task    {@code null} for a free-form goal
 * @param profile the profile that activated the strategies
 * @param memory  what this training remembers; {@link MemoryStore#NONE} outside a training
 */
public final class RunContext {

	private final UUID runId;
	private final String goal;
	private final Task task;
	private final Soul soul;
	private final Profile profile;
	private final MemoryStore memory;
	private final List<String> promptAdditions = new ArrayList<>();
	private final List<ToolCallback> extraTools = new ArrayList<>();

	public RunContext(UUID runId, String goal, Task task, Soul soul, Profile profile, MemoryStore memory) {
		this.runId = runId;
		this.goal = goal;
		this.task = task;
		this.soul = soul;
		this.profile = profile;
		this.memory = memory == null ? MemoryStore.NONE : memory;
	}

	/** What this training remembers so far, scoped to training and strategy. */
	public MemoryStore memory() {
		return memory;
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
