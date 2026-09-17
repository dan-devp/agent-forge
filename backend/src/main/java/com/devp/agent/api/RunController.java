package com.devp.agent.api;

import java.util.List;
import java.util.UUID;

import com.devp.agent.run.AgentRun;
import com.devp.agent.run.AgentService;

import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 * Laeufe des Agenten.
 *
 * <p>{@code POST /api/runs} arbeitet synchron: der Aufruf kommt zurueck, wenn der Lauf
 * fertig ist oder sein Budget aufgebraucht hat. Fuer Fortschritt waehrend des Laufs
 * fehlt noch ein Stream — bis dahin begrenzt das Budget die Wartezeit.
 */
@RestController
@RequestMapping("/api/runs")
@Validated
public class RunController {

	private final AgentService agent;

	public RunController(AgentService agent) {
		this.agent = agent;
	}

	@PostMapping
	public AgentRun start(@Validated @RequestBody RunRequest request) {
		return agent.run(request.goal(), request.task(), request.provider(), request.soul());
	}

	/** Uebersicht ohne Trace. */
	@GetMapping
	public List<AgentRun> history() {
		return agent.history();
	}

	/** Einzelner Lauf inklusive vollstaendigem Trace. */
	@GetMapping("/{id}")
	public AgentRun detail(@PathVariable UUID id) {
		return agent.find(id).orElseThrow(() -> new UnknownRunException("Unbekannter Lauf '%s'.".formatted(id)));
	}

	/**
	 * Entweder {@code task} (benannte Aufgabe) oder {@code goal} (freier Auftrag);
	 * {@code provider} und {@code soul} ueberschreiben die Vorgaben der Aufgabe.
	 */
	public record RunRequest(
			@Size(max = 4000, message = "goal ist zu lang (max. 4000 Zeichen)")
			String goal,

			String task,

			String provider,

			String soul) {
	}

	/** Angefragte Lauf-Id existiert nicht. */
	public static class UnknownRunException extends RuntimeException {
		public UnknownRunException(String message) {
			super(message);
		}
	}
}
