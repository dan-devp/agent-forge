package com.devp.forge.api;

import java.util.List;

import com.devp.forge.agent.Agent;
import com.devp.forge.agent.AgentRepository;
import com.devp.forge.agent.ToolAccess;
import com.devp.forge.model.ModelKind;
import com.devp.forge.model.ModelRepository;
import com.devp.forge.run.RunRepository;
import com.devp.forge.store.StoreExceptions.EntityInUseException;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;
import com.devp.forge.tools.ToolRegistry;
import com.devp.forge.training.TrainingRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Agents: model + system prompt + tool allowlist, versioned. */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

	private final AgentRepository agents;
	private final ModelRepository models;
	private final ToolRegistry tools;
	private final RunRepository runs;
	private final TrainingRepository trainings;

	public AgentController(AgentRepository agents, ModelRepository models, ToolRegistry tools, RunRepository runs,
			TrainingRepository trainings) {
		this.agents = agents;
		this.models = models;
		this.tools = tools;
		this.runs = runs;
		this.trainings = trainings;
	}

	@GetMapping
	public List<AgentView> list() {
		return agents.list().stream().map(this::view).toList();
	}

	@GetMapping("/{id}")
	public AgentDetail get(@PathVariable String id) {
		Agent agent = agents.get(id);
		return new AgentDetail(view(agent), agents.versions(id).stream().map(Agent::version).toList(),
				runs.countByAgent(id));
	}

	@GetMapping("/{id}/versions/{version}")
	public AgentView version(@PathVariable String id, @PathVariable int version) {
		return view(agents.get(id, version));
	}

	@GetMapping(value = "/{id}/versions/{version}/source", produces = MediaType.TEXT_MARKDOWN_VALUE)
	public String source(@PathVariable String id, @PathVariable int version) {
		return agents.source(id, version);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AgentView create(@RequestBody AgentRequest request) {
		if (request.id() == null || request.id().isBlank()) {
			throw new EntityInvalidException("'id' is required");
		}
		requireModel(request.model());
		return view(agents.create(request.toAgent(request.id())));
	}

	@PutMapping("/{id}")
	public AgentView update(@PathVariable String id, @RequestBody AgentRequest request) {
		requireModel(request.model());
		return view(agents.update(request.toAgent(id)));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void archive(@PathVariable String id) {
		agents.get(id);
		List<String> using = trainings.list().stream()
				.filter(t -> t.agentId().equals(id) || t.coachId().equals(id))
				.map(t -> t.id())
				.toList();
		if (!using.isEmpty()) {
			throw new EntityInUseException(
					"Agent '%s' is trained or coaches in training(s) %s - change them first.".formatted(id, using));
		}
		agents.archive(id);
	}

	@PostMapping("/reload")
	public List<AgentView> reload() {
		agents.reload();
		return list();
	}

	/** A missing model is a client error on save, not a surprise at run time. */
	private void requireModel(String modelId) {
		if (modelId == null || modelId.isBlank()) {
			throw new EntityInvalidException("'model' is required");
		}
		if (!models.exists(modelId)) {
			throw new EntityInvalidException("model '%s' does not exist".formatted(modelId));
		}
		if (models.get(modelId).kind() != ModelKind.CHAT) {
			throw new EntityInvalidException(
					"model '%s' is a decision model: it answers typed questions and cannot drive an agent".formatted(modelId));
		}
	}

	private AgentView view(Agent a) {
		List<String> registered = tools.list().stream().map(ToolRegistry.ToolInfo::name).toList();
		List<String> effective = registered.stream().filter(a.tools()::allows).toList();
		List<String> missing = a.tools() instanceof ToolAccess.Only only
				? only.names().stream().filter(n -> !registered.contains(n)).toList()
				: List.of();
		return new AgentView(a.id(), a.version(), a.name(), a.description(), a.modelId(), models.exists(a.modelId()),
				a.tools().toFrontMatter(), effective, missing, a.systemPrompt());
	}

	/**
	 * {@code tools} is {@code "all"}, {@code "none"} or a list, as in the file. {@code effectiveTools}
	 * is what the model would actually be offered right now; {@code missingTools} names entries
	 * of the allowlist no registered tool matches — usually a typo.
	 */
	public record AgentView(String id, int version, String name, String description, String model, boolean modelExists,
			Object tools, List<String> effectiveTools, List<String> missingTools, String systemPrompt) {
	}

	public record AgentDetail(AgentView agent, List<Integer> versions, int runs) {
	}

	/** {@code tools}: {@code "all"}, {@code "none"}, or a JSON array of names. */
	public record AgentRequest(String id, String name, String description, String model, Object tools,
			String systemPrompt) {

		Agent toAgent(String id) {
			return new Agent(id, 0, name, description, model, toolAccess(), systemPrompt);
		}

		private ToolAccess toolAccess() {
			if (tools == null) {
				return ToolAccess.ALL;
			}
			if (tools instanceof List<?> list) {
				return ToolAccess.of(list.stream().map(String::valueOf).map(String::strip).filter(s -> !s.isEmpty())
						.toList());
			}
			return switch (String.valueOf(tools).strip().toLowerCase()) {
				case "all" -> ToolAccess.ALL;
				case "none", "" -> ToolAccess.NONE;
				default -> throw new EntityInvalidException("'tools' must be \"all\", \"none\" or a list of names");
			};
		}
	}
}
