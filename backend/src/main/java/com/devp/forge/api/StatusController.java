package com.devp.forge.api;

import com.devp.forge.agent.AgentRepository;
import com.devp.forge.config.DataProperties;
import com.devp.forge.model.ModelRepository;
import com.devp.forge.task.TaskRepository;
import com.devp.forge.tools.ToolRegistry;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** One glance: where the data lives and how much of it there is. */
@RestController
@RequestMapping("/api/status")
public class StatusController {

	private final DataProperties data;
	private final ModelRepository models;
	private final AgentRepository agents;
	private final TaskRepository tasks;
	private final ToolRegistry tools;

	public StatusController(DataProperties data, ModelRepository models, AgentRepository agents,
			TaskRepository tasks, ToolRegistry tools) {
		this.data = data;
		this.models = models;
		this.agents = agents;
		this.tasks = tasks;
		this.tools = tools;
	}

	@GetMapping
	public Status status() {
		return new Status(data.getDataDir().toAbsolutePath().normalize().toString(), models.list().size(),
				agents.list().size(), tasks.list().size(), tools.list().size());
	}

	public record Status(String dataDir, int models, int agents, int tasks, int tools) {
	}
}
