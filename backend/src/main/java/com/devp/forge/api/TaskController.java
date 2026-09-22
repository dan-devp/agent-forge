package com.devp.forge.api;

import java.util.List;
import java.util.Map;

import com.devp.forge.eval.EvalConfig;
import com.devp.forge.run.RunRepository;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;
import com.devp.forge.task.Task;
import com.devp.forge.task.TaskRepository;

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

/** Tasks: input + evals, versioned. Eval entries are validated against the registry on save. */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

	private final TaskRepository tasks;
	private final RunRepository runs;

	public TaskController(TaskRepository tasks, RunRepository runs) {
		this.tasks = tasks;
		this.runs = runs;
	}

	@GetMapping
	public List<Task> list() {
		return tasks.list();
	}

	@GetMapping("/{id}")
	public TaskDetail get(@PathVariable String id) {
		Task task = tasks.get(id);
		return new TaskDetail(task, tasks.versions(id).stream().map(Task::version).toList(), runs.countByTask(id));
	}

	@GetMapping("/{id}/versions/{version}")
	public Task version(@PathVariable String id, @PathVariable int version) {
		return tasks.get(id, version);
	}

	@GetMapping(value = "/{id}/versions/{version}/source", produces = MediaType.TEXT_MARKDOWN_VALUE)
	public String source(@PathVariable String id, @PathVariable int version) {
		return tasks.source(id, version);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Task create(@RequestBody TaskRequest request) {
		if (request.id() == null || request.id().isBlank()) {
			throw new EntityInvalidException("'id' is required");
		}
		return tasks.create(request.toTask(request.id()));
	}

	@PutMapping("/{id}")
	public Task update(@PathVariable String id, @RequestBody TaskRequest request) {
		return tasks.update(request.toTask(id));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void archive(@PathVariable String id) {
		tasks.archive(id);
	}

	@PostMapping("/reload")
	public List<Task> reload() {
		tasks.reload();
		return list();
	}

	public record TaskDetail(Task task, List<Integer> versions, int runs) {
	}

	public record TaskRequest(String id, String name, String description, Integer maxToolCalls,
			List<EvalRequest> evals, String input) {

		Task toTask(String id) {
			List<EvalConfig> configs = evals == null
					? List.of()
					: evals.stream().map(e -> new EvalConfig(e.type(), e.params() == null ? Map.of() : e.params()))
							.toList();
			return new Task(id, 0, name, description, maxToolCalls, configs, input);
		}
	}

	public record EvalRequest(String type, Map<String, Object> params) {
	}
}
