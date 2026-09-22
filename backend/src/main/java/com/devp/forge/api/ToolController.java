package com.devp.forge.api;

import java.util.List;

import com.devp.forge.tools.ToolRegistry;
import com.devp.forge.tools.ToolRegistry.ServerInfo;
import com.devp.forge.tools.ToolRegistry.ToolInfo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Registered tools (built-in and MCP) and the state of the MCP servers. */
@RestController
@RequestMapping("/api/tools")
public class ToolController {

	private final ToolRegistry registry;

	public ToolController(ToolRegistry registry) {
		this.registry = registry;
	}

	@GetMapping
	public ToolList list() {
		return new ToolList(registry.list(), registry.servers());
	}

	@PostMapping("/reload")
	public ToolList reload() {
		registry.reload();
		return list();
	}

	public record ToolList(List<ToolInfo> tools, List<ServerInfo> servers) {
	}
}
