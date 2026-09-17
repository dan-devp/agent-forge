package com.devp.agent.api;

import java.util.List;
import java.util.Map;

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
 * SOUL management. Read plus reload — editing happens in the files so the history stays
 * in Git.
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
		String active = souls.activeId();
		List<SoulSummary> list = souls.list().stream()
				.map(soul -> SoulSummary.of(soul, soul.id().equals(active)))
				.toList();
		return new SoulListResponse(active, list);
	}

	/** Including the rendered prompt — exactly what the model gets to see. */
	@GetMapping("/{id}")
	public SoulDetail detail(@PathVariable String id) {
		Soul soul = souls.resolve(id);
		return new SoulDetail(
				SoulSummary.of(soul, soul.id().equals(souls.activeId())),
				soul.description(),
				soul.sections(),
				soul.source(),
				renderer.render(soul));
	}

	/** Re-reads the SOUL files; takes effect on the next model call. */
	@PostMapping("/reload")
	public SoulListResponse reload() {
		souls.reload();
		return list();
	}

	public record SoulSummary(String id, String name, String role, int version, List<SoulGate> gates,
			boolean isActive) {

		static SoulSummary of(Soul soul, boolean active) {
			return new SoulSummary(soul.id(), soul.name(), soul.role(), soul.version(), soul.gates(), active);
		}
	}

	public record SoulListResponse(String activeSoul, List<SoulSummary> souls) {
	}

	public record SoulDetail(SoulSummary soul, String description, Map<String, String> sections, String source,
			String prompt) {
	}
}
