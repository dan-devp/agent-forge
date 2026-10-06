package com.devp.forge.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.devp.forge.agent.AgentRepository;
import com.devp.forge.store.StoreExceptions.EntityInUseException;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;
import com.devp.forge.task.TaskRepository;
import com.devp.forge.training.Approval;
import com.devp.forge.training.Session;
import com.devp.forge.training.Step;
import com.devp.forge.training.Training;
import com.devp.forge.training.TrainingEngine;
import com.devp.forge.training.TrainingRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trainings (versioned files) and their sessions (events in the database). Starting a
 * session returns at once; the loop runs on its own thread and is followed by polling.
 */
@RestController
public class TrainingController {

	private final TrainingRepository trainings;
	private final AgentRepository agents;
	private final TaskRepository tasks;
	private final TrainingEngine engine;

	public TrainingController(TrainingRepository trainings, AgentRepository agents, TaskRepository tasks,
			TrainingEngine engine) {
		this.trainings = trainings;
		this.agents = agents;
		this.tasks = tasks;
		this.engine = engine;
	}

	@GetMapping("/api/trainings")
	public List<TrainingView> list() {
		return trainings.list().stream().map(t -> {
			List<Session> history = engine.history(t.id());
			return new TrainingView(t, history.isEmpty() ? null : history.getFirst());
		}).toList();
	}

	@GetMapping("/api/trainings/{id}")
	public TrainingDetail get(@PathVariable String id) {
		Training training = trainings.get(id);
		return new TrainingDetail(training, trainings.versions(id).stream().map(Training::version).toList(),
				engine.history(id));
	}

	@GetMapping("/api/trainings/{id}/versions/{version}")
	public Training version(@PathVariable String id, @PathVariable int version) {
		return trainings.get(id, version);
	}

	@GetMapping(value = "/api/trainings/{id}/versions/{version}/source", produces = MediaType.TEXT_MARKDOWN_VALUE)
	public String source(@PathVariable String id, @PathVariable int version) {
		return trainings.source(id, version);
	}

	@PostMapping("/api/trainings")
	@ResponseStatus(HttpStatus.CREATED)
	public Training create(@RequestBody TrainingRequest request) {
		if (request.id() == null || request.id().isBlank()) {
			throw new EntityInvalidException("'id' is required");
		}
		Training draft = request.toTraining(request.id());
		requireReferences(draft);
		return trainings.create(draft);
	}

	@PutMapping("/api/trainings/{id}")
	public Training update(@PathVariable String id, @RequestBody TrainingRequest request) {
		Training draft = request.toTraining(id);
		requireReferences(draft);
		return trainings.update(draft);
	}

	/** Archives the file. Sessions keep id and version; a running one must be stopped first. */
	@DeleteMapping("/api/trainings/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void archive(@PathVariable String id) {
		trainings.get(id);
		if (engine.isOpen(id)) {
			throw new EntityInUseException("Training '%s' has a running session - stop it first.".formatted(id));
		}
		trainings.archive(id);
	}

	@PostMapping("/api/trainings/reload")
	public List<TrainingView> reload() {
		trainings.reload();
		return list();
	}

	/** Body optional: {@code {"startVersion": 3}} starts from that agent version, without it from the current one. */
	@PostMapping("/api/trainings/{id}/sessions")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public Session start(@PathVariable String id, @RequestBody(required = false) StartRequest request) {
		return engine.start(id, request == null ? null : request.startVersion());
	}

	@GetMapping("/api/sessions/{id}")
	public Session session(@PathVariable UUID id) {
		return engine.find(id);
	}

	@PostMapping("/api/sessions/{id}/stop")
	public Map<String, Boolean> stop(@PathVariable UUID id) {
		return Map.of("stopping", engine.stop(id));
	}

	@PostMapping("/api/sessions/{id}/steps/{seq}/decision")
	public Step decide(@PathVariable UUID id, @PathVariable int seq, @RequestBody DecisionRequest request) {
		if (request.accept() == null) {
			throw new EntityInvalidException("'accept' is required");
		}
		return engine.decide(id, seq, request.accept());
	}

	/** A training that names a missing agent or task is a client error on save, not a surprise at start. */
	private void requireReferences(Training t) {
		for (String agent : List.of(t.agentId(), t.coachId())) {
			if (!agents.exists(agent)) {
				throw new EntityInvalidException("agent '%s' does not exist".formatted(agent));
			}
		}
		for (String task : t.allTasks()) {
			if (!tasks.exists(task)) {
				throw new EntityInvalidException("task '%s' does not exist".formatted(task));
			}
		}
	}

	/** The training with its newest session, for the list. */
	public record TrainingView(Training training, Session lastSession) {
	}

	public record TrainingDetail(Training training, List<Integer> versions, List<Session> sessions) {
	}

	public record TrainingRequest(String id, String name, String description, String agent, String coach,
			List<String> train, List<String> holdout, Integer repetitions, Integer maxSteps, Integer maxTokens,
			String approval, String goal) {

		Training toTraining(String id) {
			return new Training(id, 0, name, description, agent, coach, train == null ? List.of() : train,
					holdout == null ? List.of() : holdout, repetitions == null ? 3 : repetitions,
					maxSteps == null ? 3 : maxSteps, maxTokens, Approval.of(approval), goal);
		}
	}

	public record DecisionRequest(Boolean accept) {
	}

	public record StartRequest(Integer startVersion) {
	}
}
