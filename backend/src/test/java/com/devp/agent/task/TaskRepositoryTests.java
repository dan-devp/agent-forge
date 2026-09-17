package com.devp.agent.task;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.devp.agent.config.TaskProperties;
import com.devp.agent.task.TaskRepository.UnknownTaskException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskRepositoryTests {

	private static TaskRepository repository(String... locations) {
		TaskProperties properties = new TaskProperties();
		properties.setLocations(List.of(locations));
		return new TaskRepository(properties);
	}

	@Test
	void loadsBundledTasks() {
		TaskRepository repository = repository("classpath:tasks/");

		assertThat(repository.list()).extracting(Task::id)
				.contains("time-and-division", "mcp-research", "no-tool");
		assertThat(repository.resolve("time-and-division").goal()).contains("100 geteilt durch 7");
	}

	/** Every bundled task must be gradable — otherwise it is worthless for the training camp. */
	@Test
	void bundledTasksCarryCriteria() {
		TaskRepository repository = repository("classpath:tasks/");

		assertThat(repository.list()).allSatisfy(task -> assertThat(task.success())
				.as("task %s without success:", task.id()).isNotEmpty());
	}

	@Test
	void reportsUnknownTask() {
		TaskRepository repository = repository("classpath:tasks/");

		assertThatThrownBy(() -> repository.resolve("does-not-exist"))
				.isInstanceOf(UnknownTaskException.class)
				.hasMessageContaining("time-and-division");
	}

	/** A later location wins — so a task set is replaceable in operation. */
	@Test
	void externalLocationOverridesSameId(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("time-and-division.md"), """
				---
				id: time-and-division
				name: Replaced
				version: 9
				---
				# Goal
				Something else.
				""");

		TaskRepository repository = repository("classpath:tasks/", dir.toUri().toString());

		Task task = repository.resolve("time-and-division");
		assertThat(task.name()).isEqualTo("Replaced");
		assertThat(task.version()).isEqualTo(9);
		assertThat(repository.resolve("no-tool").name()).isEqualTo("Luecke benennen");
	}

	@Test
	void reloadPicksUpNewFiles(@TempDir Path dir) throws Exception {
		TaskRepository repository = repository(dir.toUri().toString());
		assertThat(repository.list()).isEmpty();

		Files.writeString(dir.resolve("new.md"), """
				---
				id: new
				---
				# Goal
				Freshly loaded.
				""");

		assertThat(repository.reload()).isEqualTo(1);
		assertThat(repository.resolve("new").goal()).isEqualTo("Freshly loaded.");
	}

	@Test
	void missingLocationIsEmptyNotAnError() {
		assertThat(repository("classpath:does-not-exist/").list()).isEmpty();
	}
}
