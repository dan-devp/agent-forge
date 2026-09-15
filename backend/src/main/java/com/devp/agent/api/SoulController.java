package com.devp.agent.api;

import java.util.List;

import com.devp.agent.soul.Soul;
import com.devp.agent.soul.SoulGate;
import com.devp.agent.soul.SoulRenderer;
import com.devp.agent.soul.SoulRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SOUL-Verwaltung. Lesend plus Reload — Bearbeiten laeuft ueber die Dateien,
 * damit die Historie im Git bleibt.
 */
@RestController
@RequestMapping("/api/souls")
public class SoulController {

	private final SoulRepository souls;
	private final SoulRenderer renderer;

	public SoulController(SoulRepository souls, SoulRenderer renderer) {
		this.souls = souls;
		this.renderer = renderer;
	}

	@GetMapping
	public SoulListResponse list() {
		String aktiv = souls.activeId();
		List<SoulSummary> liste = souls.list().stream()
				.map(soul -> SoulSummary.von(soul, soul.id().equals(aktiv)))
				.toList();
		return new SoulListResponse(aktiv, liste);
	}

	/** Inklusive gerendertem Prompt — genau das, was das Modell zu sehen bekommt. */
	@GetMapping("/{id}")
	public SoulDetail detail(@PathVariable String id) {
		Soul soul = souls.resolve(id);
		return new SoulDetail(
				SoulSummary.von(soul, soul.id().equals(souls.activeId())),
				soul.beschreibung(),
				soul.abschnitte(),
				soul.quelle(),
				renderer.render(soul));
	}

	/** Liest die SOUL-Dateien neu ein; wirkt sofort auf den naechsten Modellaufruf. */
	@PostMapping("/reload")
	public SoulListResponse reload() {
		souls.reload();
		return list();
	}

	public record SoulSummary(String id, String name, String rolle, int version, List<SoulGate> gates,
			boolean isActive) {

		static SoulSummary von(Soul soul, boolean aktiv) {
			return new SoulSummary(soul.id(), soul.name(), soul.rolle(), soul.version(), soul.gates(), aktiv);
		}
	}

	public record SoulListResponse(String activeSoul, List<SoulSummary> souls) {
	}

	public record SoulDetail(SoulSummary soul, String beschreibung, java.util.Map<String, String> abschnitte,
			String quelle, String prompt) {
	}
}
