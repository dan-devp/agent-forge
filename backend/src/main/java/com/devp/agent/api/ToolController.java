package com.devp.agent.api;

import java.util.List;

import com.devp.agent.tools.ToolRegistry;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Werkzeuge und registrierte MCP-Server.
 *
 * <p>Lesend plus Reload — registriert wird ueber {@code app.tools.mcp.<id>}, damit die
 * Konfiguration eines Servers versionierbar bleibt statt in einer Laufzeit-Datenbank zu
 * verschwinden.
 */
@RestController
@RequestMapping("/api/tools")
public class ToolController {

	private final ToolRegistry tools;

	public ToolController(ToolRegistry tools) {
		this.tools = tools;
	}

	@GetMapping
	public ToolListResponse list() {
		return new ToolListResponse(tools.list(), tools.servers());
	}

	/** Verbindet alle MCP-Server neu — noetig, wenn ein Server neu gestartet wurde. */
	@PostMapping("/reload")
	public ToolListResponse reload() {
		tools.reload();
		return list();
	}

	public record ToolListResponse(List<ToolRegistry.ToolInfo> tools, List<ToolRegistry.ServerInfo> servers) {
	}
}
