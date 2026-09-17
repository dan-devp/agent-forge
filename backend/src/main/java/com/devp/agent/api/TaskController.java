package com.devp.agent.api;

import java.util.List;

import com.devp.agent.task.Task;
import com.devp.agent.task.TaskRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tasks.
 *
 * <p>Read plus reload — editing happens in the files so the history stays in Git. The same
 * decision as for SOULs.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

	private final TaskRepository tasks;

	public TaskController(TaskRepository tasks) {
		this.tasks = tasks;
	}

	@GetMapping
	public List<TaskSummary> list() {
		return tasks.list().stream().map(TaskSummary::of).toList();
	}

	/** Including the goal text and the further sections of the file. */
	@GetMapping("/{id}")
	public Task detail(@PathVariable String id) {
		return tasks.resolve(id);
	}

	@PostMapping("/reload")
	public List<TaskSummary> reload() {
		tasks.reload();
		return list();
	}

	/** Overview without the full text. */
	public record TaskSummary(String id, String name, int version, String description, int difficulty,
			List<String> tags, String soul, String provider, String profile) {

		static TaskSummary of(Task task) {
			return new TaskSummary(task.id(), task.name(), task.version(), task.description(),
					task.difficulty(), task.tags(), task.soul(), task.provider(), task.profile());
		}
	}
}
