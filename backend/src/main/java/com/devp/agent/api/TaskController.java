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
 * Aufgaben.
 *
 * <p>Lesend plus Reload — bearbeitet wird in den Dateien, damit die Historie im Git bleibt.
 * Dieselbe Entscheidung wie bei den SOULs.
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
		return tasks.list().stream().map(TaskSummary::von).toList();
	}

	/** Inklusive Auftragstext und weiteren Abschnitten der Datei. */
	@GetMapping("/{id}")
	public Task detail(@PathVariable String id) {
		return tasks.resolve(id);
	}

	@PostMapping("/reload")
	public List<TaskSummary> reload() {
		tasks.reload();
		return list();
	}

	/** Uebersicht ohne den vollen Text. */
	public record TaskSummary(String id, String name, int version, String beschreibung, int schwierigkeit,
			List<String> tags, String soul, String provider) {

		static TaskSummary von(Task task) {
			return new TaskSummary(task.id(), task.name(), task.version(), task.beschreibung(),
					task.schwierigkeit(), task.tags(), task.soul(), task.provider());
		}
	}
}
