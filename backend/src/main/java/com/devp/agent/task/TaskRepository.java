package com.devp.agent.task;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.devp.agent.config.TaskProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Loads all tasks from the configured locations and keeps them in memory.
 *
 * <p>Same pattern as {@code SoulRepository}: definitions are files, their history lives in
 * Git, and {@link #reload()} makes a changed task effective without a restart.
 */
@Component
public class TaskRepository {

	private static final Logger log = LoggerFactory.getLogger(TaskRepository.class);

	private final TaskProperties properties;
	private final ResourcePatternResolver resolver;

	private volatile Map<String, Task> tasks = Map.of();

	public TaskRepository(TaskProperties properties) {
		this.properties = properties;
		this.resolver = new PathMatchingResourcePatternResolver();
		reload();
	}

	/** Re-reads all locations. Returns the number of tasks found. */
	public int reload() {
		Map<String, Task> loaded = new LinkedHashMap<>();

		for (String location : properties.getLocations()) {
			String pattern = location.endsWith("/") ? location + "*.md" : location + "/*.md";
			for (Resource resource : find(pattern)) {
				Task task = read(resource);
				Task previous = loaded.put(task.id(), task);
				if (previous != null) {
					log.info("Task '{}' from {} overrides {}", task.id(), task.source(), previous.source());
				}
			}
		}

		// LinkedHashMap instead of Map.copyOf: load order determines display order.
		this.tasks = Collections.unmodifiableMap(loaded);
		log.info("{} task(s) loaded: {}", loaded.size(), loaded.keySet());
		return loaded.size();
	}

	private Resource[] find(String pattern) {
		try {
			return resolver.getResources(pattern);
		}
		catch (IOException ex) {
			// A missing external location is not an error, just empty.
			log.warn("Task location '{}' not readable: {}", pattern, ex.getMessage());
			return new Resource[0];
		}
	}

	private Task read(Resource resource) {
		String source = resource.getFilename() == null ? resource.getDescription() : resource.getFilename();
		try (var in = resource.getInputStream()) {
			return TaskParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), source);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Task '%s' not readable".formatted(source), ex);
		}
	}

	/** Task by id. Unlike SOULs there is no active default — a task is always named. */
	public Task resolve(String id) {
		Task task = tasks.get(id);
		if (task == null) {
			throw new UnknownTaskException(
					"Unknown task '%s'. Available: %s".formatted(id, tasks.keySet()));
		}
		return task;
	}

	public Collection<Task> list() {
		return List.copyOf(tasks.values());
	}

	/** The requested task id does not exist. */
	public static class UnknownTaskException extends RuntimeException {
		public UnknownTaskException(String message) {
			super(message);
		}
	}
}
