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
 * Laedt alle Aufgaben aus den konfigurierten Locations und haelt sie im Speicher.
 *
 * <p>Gleiches Muster wie {@code SoulRepository}: Definitionen sind Dateien, ihre Historie
 * liegt im Git, und {@link #reload()} macht eine geaenderte Aufgabe ohne Neustart wirksam.
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

	/** Liest alle Locations neu ein. Gibt die Anzahl gefundener Aufgaben zurueck. */
	public int reload() {
		Map<String, Task> geladen = new LinkedHashMap<>();

		for (String location : properties.getLocations()) {
			String muster = location.endsWith("/") ? location + "*.md" : location + "/*.md";
			for (Resource resource : suche(muster)) {
				Task task = lies(resource);
				Task vorher = geladen.put(task.id(), task);
				if (vorher != null) {
					log.info("Aufgabe '{}' aus {} ueberschreibt {}", task.id(), task.quelle(), vorher.quelle());
				}
			}
		}

		// LinkedHashMap statt Map.copyOf: die Ladereihenfolge bestimmt die Anzeige.
		this.tasks = Collections.unmodifiableMap(geladen);
		log.info("{} Aufgabe(n) geladen: {}", geladen.size(), geladen.keySet());
		return geladen.size();
	}

	private Resource[] suche(String muster) {
		try {
			return resolver.getResources(muster);
		}
		catch (IOException ex) {
			// Eine nicht existierende externe Location ist kein Fehler, nur leer.
			log.warn("Aufgaben-Location '{}' nicht lesbar: {}", muster, ex.getMessage());
			return new Resource[0];
		}
	}

	private Task lies(Resource resource) {
		String quelle = resource.getFilename() == null ? resource.getDescription() : resource.getFilename();
		try (var in = resource.getInputStream()) {
			return TaskParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), quelle);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Aufgabe '%s' nicht lesbar".formatted(quelle), ex);
		}
	}

	/** Aufgabe zur Id. Anders als bei SOULs gibt es keine aktive Vorgabe — sie wird immer benannt. */
	public Task resolve(String id) {
		Task task = tasks.get(id);
		if (task == null) {
			throw new UnknownTaskException(
					"Unbekannte Aufgabe '%s'. Verfuegbar: %s".formatted(id, tasks.keySet()));
		}
		return task;
	}

	public Collection<Task> list() {
		return List.copyOf(tasks.values());
	}

	/** Angefragte Aufgaben-Id existiert nicht. */
	public static class UnknownTaskException extends RuntimeException {
		public UnknownTaskException(String message) {
			super(message);
		}
	}
}
