package com.devp.agent.api;

import java.util.List;
import java.util.Map;

import com.devp.agent.agent.Agent;
import com.devp.agent.agent.AgentRepository;
import com.devp.agent.tools.ToolRegistry;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The agent catalogue: who can be put to work, with which model, identity and tools.
 *
 * <p>Read-only, like SOULs, tasks and profiles — an agent is edited in its file so its
 * history stays in Git. The listing resolves the tool allowlist against what is actually
 * registered: a name the allowlist asks for and no server provides has to be visible, or an
 * agent silently works with fewer tools than its file claims.
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

	private final AgentRepository agents;
	private final ToolRegistry tools;

	public AgentController(AgentRepository agents, ToolRegistry tools) {
		this.agents = agents;
		this.tools = tools;
	}

	@GetMapping
	public AgentListResponse list() {
		return new AgentListResponse(agents.defaultId(), agents.list().stream().map(this::describe).toList());
	}

	@GetMapping("/{id}")
	public AgentView one(@PathVariable String id) {
		return describe(agents.resolve(id));
	}

	@PostMapping("/reload")
	public AgentListResponse reload() {
		agents.reload();
		return list();
	}

	private AgentView describe(Agent agent) {
		List<String> available = tools.list().stream().map(ToolRegistry.ToolInfo::name).toList();
		List<String> effective = switch (agent.tools().mode()) {
			case ALL -> available;
			case NONE -> List.of();
			case ALLOWLIST -> available.stream().filter(agent.tools()::allows).toList();
		};
		List<String> missing = agent.tools().names().stream().filter(name -> !available.contains(name)).toList();
		return new AgentView(agent.id(), agent.name(), agent.version(), agent.description(), agent.provider(),
				agent.soul(), agent.tools().mode().name(), agent.tools().names(), effective, missing,
				agent.sections(), agent.source());
	}

	/**
	 * @param toolMode      ALL, NONE or ALLOWLIST
	 * @param tools         what the file asks for; empty for ALL and NONE
	 * @param effectiveTools what the agent would actually be offered right now
	 * @param missingTools  asked for but not registered — an MCP server that is down, or a typo
	 */
	public record AgentView(String id, String name, int version, String description, String provider, String soul,
			String toolMode, List<String> tools, List<String> effectiveTools, List<String> missingTools,
			Map<String, String> sections, String source) {
	}

	public record AgentListResponse(String defaultAgent, List<AgentView> agents) {
	}
}
