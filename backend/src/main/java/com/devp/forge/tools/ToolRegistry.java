package com.devp.forge.tools;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.devp.forge.config.ToolProperties;
import com.devp.forge.config.ToolProperties.McpServer;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * All tools an agent can request: built-in ones plus the tools of the registered MCP
 * servers.
 *
 * <p>The MCP clients are built by us instead of via the Spring AI autoconfiguration. The
 * reason is the same as for {@code ChatClientRegistry}: only that way can a server be
 * reconnected at runtime ({@link #reload()}), and the configuration keeps the familiar
 * {@code app.*} shape.
 *
 * <p>A broken MCP server must not prevent startup — it is listed as not connected and its
 * error stays visible.
 */
@Component
public class ToolRegistry {

	private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

	private final ToolProperties properties;
	private final BuiltinTools builtinTools;
	private final McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(JsonMapper.builder().build());

	private volatile List<ToolCallback> callbacks = List.of();
	private volatile List<McpSyncClient> clients = List.of();
	private volatile List<ServerInfo> servers = List.of();

	public ToolRegistry(ToolProperties properties, BuiltinTools builtinTools) {
		this.properties = properties;
		this.builtinTools = builtinTools;
		reload();
	}

	/** Reconnects all servers. Returns the number of available tools. */
	public synchronized int reload() {
		closeClients();

		List<ToolCallback> all = new ArrayList<>();
		List<McpSyncClient> connected = new ArrayList<>();
		List<ServerInfo> infos = new ArrayList<>();

		if (properties.isBuiltinEnabled()) {
			all.addAll(List.of(MethodToolCallbackProvider.builder()
					.toolObjects(builtinTools)
					.build()
					.getToolCallbacks()));
		}

		properties.getMcp().forEach((id, config) -> {
			if (!config.isEnabled()) {
				infos.add(ServerInfo.disabled(id, config));
				return;
			}
			if (!config.isConfigured()) {
				infos.add(ServerInfo.failed(id, config, "not fully configured "
						+ "(STDIO needs 'command', SSE/HTTP need 'url')"));
				return;
			}
			try {
				McpSyncClient client = connect(id, config);
				List<ToolCallback> tools = SyncMcpToolCallbackProvider.syncToolCallbacks(List.of(client));
				all.addAll(tools);
				connected.add(client);
				infos.add(ServerInfo.connected(id, config, tools.size()));
				log.info("MCP server '{}' connected: {} tool(s)", id, tools.size());
			}
			catch (RuntimeException ex) {
				infos.add(ServerInfo.failed(id, config, ex.getMessage()));
				log.warn("MCP server '{}' not reachable: {}", id, ex.getMessage());
			}
		});

		this.callbacks = List.copyOf(all);
		this.clients = List.copyOf(connected);
		this.servers = List.copyOf(infos);
		log.info("{} tool(s) available, {} MCP server(s) connected", all.size(), connected.size());
		return all.size();
	}

	private McpSyncClient connect(String id, McpServer config) {
		McpClientTransport transport = switch (config.getTransport()) {
			case STDIO -> new StdioClientTransport(
					ServerParameters.builder(config.getCommand())
							.args(config.getArgs())
							.env(config.getEnv())
							.build(),
					jsonMapper);
			case SSE -> HttpClientSseClientTransport.builder(config.getUrl())
					.jsonMapper(jsonMapper)
					.connectTimeout(Duration.ofMillis(config.getTimeoutMs()))
					.build();
			case HTTP -> HttpClientStreamableHttpTransport.builder(config.getUrl())
					.jsonMapper(jsonMapper)
					.build();
		};

		McpSyncClient client = McpClient.sync(transport)
				.requestTimeout(Duration.ofMillis(config.getTimeoutMs()))
				.initializationTimeout(Duration.ofMillis(config.getTimeoutMs()))
				.build();
		client.initialize();
		log.debug("MCP server '{}' identifies as {}", id, client.getServerInfo());
		return client;
	}

	private void closeClients() {
		clients.forEach(client -> {
			try {
				client.closeGracefully();
			}
			catch (RuntimeException ex) {
				log.warn("MCP client did not close cleanly: {}", ex.getMessage());
			}
		});
		clients = List.of();
	}

	@PreDestroy
	public void shutdown() {
		closeClients();
	}

	/** All tools offered to the model. */
	public List<ToolCallback> callbacks() {
		return callbacks;
	}

	/** Description of all tools for the frontend. */
	public List<ToolInfo> list() {
		return callbacks.stream()
				.map(callback -> new ToolInfo(
						callback.getToolDefinition().name(),
						callback.getToolDefinition().description(),
						sourceOf(callback)))
				.toList();
	}

	public List<ServerInfo> servers() {
		return servers;
	}

	/**
	 * Where does the tool come from? MCP callbacks originate from the Spring AI MCP package,
	 * everything else is a built-in method tool.
	 */
	private static String sourceOf(ToolCallback callback) {
		return callback.getClass().getName().startsWith("org.springframework.ai.mcp") ? "mcp" : "builtin";
	}

	/** A tool as shown to the model and the frontend. */
	public record ToolInfo(String name, String description, String source) {
	}

	/** State of a registered MCP server. */
	public record ServerInfo(String id, String label, ToolProperties.Transport transport, String target,
			boolean enabled, boolean connected, int toolCount, String error) {

		static ServerInfo connected(String id, McpServer config, int toolCount) {
			return new ServerInfo(id, label(id, config), config.getTransport(), target(config), true, true,
					toolCount, null);
		}

		static ServerInfo failed(String id, McpServer config, String error) {
			return new ServerInfo(id, label(id, config), config.getTransport(), target(config), true, false, 0,
					error);
		}

		static ServerInfo disabled(String id, McpServer config) {
			return new ServerInfo(id, label(id, config), config.getTransport(), target(config), false, false, 0,
					null);
		}

		private static String label(String id, McpServer config) {
			return config.getLabel() == null || config.getLabel().isBlank() ? id : config.getLabel();
		}

		private static String target(McpServer config) {
			return config.getTransport() == ToolProperties.Transport.STDIO
					? (config.getCommand() + " " + String.join(" ", config.getArgs())).strip()
					: config.getUrl();
		}
	}

	/** Diagnostics only: which environment does a stdio server receive? */
	Map<String, String> envOf(String serverId) {
		McpServer config = properties.getMcp().get(serverId);
		return config == null ? Map.of() : config.getEnv();
	}
}
