package com.devp.forge.api;

import java.util.List;

import com.devp.forge.agent.Agent;
import com.devp.forge.agent.AgentRepository;
import com.devp.forge.ai.ChatClientRegistry;
import com.devp.forge.model.Model;
import com.devp.forge.model.ModelRepository;
import com.devp.forge.run.RunRepository;
import com.devp.forge.secrets.SecretResolver;
import com.devp.forge.store.StoreExceptions.EntityInUseException;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;

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

/**
 * Models: create, save as new version, archive.
 *
 * <p>The key never appears here. A model names an environment variable; the response says
 * whether that variable is set, nothing more.
 */
@RestController
@RequestMapping("/api/models")
public class ModelController {

	private final ModelRepository models;
	private final AgentRepository agents;
	private final ChatClientRegistry clients;
	private final SecretResolver secrets;
	private final RunRepository runs;

	public ModelController(ModelRepository models, AgentRepository agents, ChatClientRegistry clients,
			SecretResolver secrets, RunRepository runs) {
		this.models = models;
		this.agents = agents;
		this.clients = clients;
		this.secrets = secrets;
		this.runs = runs;
	}

	@GetMapping
	public List<ModelView> list() {
		return models.list().stream().map(this::view).toList();
	}

	@GetMapping("/{id}")
	public ModelDetail get(@PathVariable String id) {
		Model model = models.get(id);
		return new ModelDetail(view(model), models.versions(id).stream().map(Model::version).toList(),
				runs.countByModel(id), agentsUsing(id));
	}

	@GetMapping("/{id}/versions/{version}")
	public ModelView version(@PathVariable String id, @PathVariable int version) {
		return view(models.get(id, version));
	}

	@GetMapping(value = "/{id}/versions/{version}/source", produces = MediaType.TEXT_MARKDOWN_VALUE)
	public String source(@PathVariable String id, @PathVariable int version) {
		return models.source(id, version);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ModelView create(@RequestBody ModelRequest request) {
		if (request.id() == null || request.id().isBlank()) {
			throw new EntityInvalidException("'id' is required");
		}
		return view(models.create(request.toModel(request.id(), 0)));
	}

	/** Writes the next version. The id in the path wins over one in the body. */
	@PutMapping("/{id}")
	public ModelView update(@PathVariable String id, @RequestBody ModelRequest request) {
		return view(models.update(request.toModel(id, 0)));
	}

	/**
	 * Archives the model. Refused while an agent's current version still points at it —
	 * the agent would silently become unusable. Past runs are no obstacle: they keep id and
	 * version, and the archived file still says what they were.
	 */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void archive(@PathVariable String id) {
		models.get(id);
		List<String> using = agentsUsing(id);
		if (!using.isEmpty()) {
			throw new EntityInUseException(
					"Model '%s' is used by agent(s) %s — change them first.".formatted(id, using));
		}
		models.archive(id);
	}

	@PostMapping("/reload")
	public List<ModelView> reload() {
		models.reload();
		return list();
	}

	private List<String> agentsUsing(String modelId) {
		return agents.list().stream().filter(a -> a.modelId().equals(modelId)).map(Agent::id).toList();
	}

	private ModelView view(Model m) {
		boolean keySet = m.apiKeyEnv() != null && secrets.isSet(m.apiKeyEnv());
		return new ModelView(m.id(), m.version(), m.name(), m.description(), m.baseUrl(), m.modelId(), m.apiKeyEnv(),
				keySet, m.temperature(), m.local(), clients.isConfigured(m), clients.problem(m));
	}

	/** A model as the UI sees it. {@code keySet} says whether the named variable has a value; the value never leaves the server. */
	public record ModelView(String id, int version, String name, String description, String baseUrl, String modelId,
			String apiKeyEnv, boolean keySet, double temperature, boolean local, boolean configured, String problem) {
	}

	public record ModelDetail(ModelView model, List<Integer> versions, int runs, List<String> usedByAgents) {
	}

	public record ModelRequest(String id, String name, String description, String baseUrl, String modelId,
			String apiKeyEnv, Double temperature, Boolean local) {

		Model toModel(String id, int version) {
			return new Model(id, version, name, description, baseUrl, modelId,
					apiKeyEnv == null || apiKeyEnv.isBlank() ? null : apiKeyEnv, temperature == null ? 0.7 : temperature,
					local != null && local);
		}
	}
}
