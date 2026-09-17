package com.devp.agent.api;

import java.util.List;

import com.devp.agent.tools.ToolRegistry;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tools and registered MCP servers.
 *
 * <p>Read plus reload — servers are registered via {@code app.tools.mcp.<id>} so a server's
 * configuration stays versionable instead of vanishing into a runtime database.
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

	/** Reconnects all MCP servers — needed after a server was restarted. */
	@PostMapping("/reload")
	public ToolListResponse reload() {
		tools.reload();
		return list();
	}

	public record ToolListResponse(List<ToolRegistry.ToolInfo> tools, List<ToolRegistry.ServerInfo> servers) {
	}
}
