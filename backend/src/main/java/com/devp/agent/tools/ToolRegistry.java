package com.devp.agent.tools;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.devp.agent.config.ToolProperties;
import com.devp.agent.config.ToolProperties.McpServer;

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
 * Alle Werkzeuge, die ein Agent anfordern kann: eingebaute plus die Tools der
 * registrierten MCP-Server.
 *
 * <p>Die MCP-Clients werden selbst gebaut statt ueber die Spring-AI-Autoconfiguration.
 * Grund ist derselbe wie bei {@code ChatClientRegistry}: nur so laesst sich ein Server
 * zur Laufzeit neu verbinden ({@link #reload()}), und die Konfiguration bleibt in der
 * gewohnten {@code app.*}-Form.
 *
 * <p>Ein kaputter MCP-Server darf den Start nicht verhindern — er wird als nicht
 * verbunden gefuehrt und sein Fehler bleibt sichtbar.
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

	/** Verbindet alle Server neu. Gibt die Anzahl verfuegbarer Tools zurueck. */
	public synchronized int reload() {
		closeClients();

		List<ToolCallback> alle = new ArrayList<>();
		List<McpSyncClient> verbunden = new ArrayList<>();
		List<ServerInfo> infos = new ArrayList<>();

		if (properties.isBuiltinEnabled()) {
			alle.addAll(List.of(MethodToolCallbackProvider.builder()
					.toolObjects(builtinTools)
					.build()
					.getToolCallbacks()));
		}

		properties.getMcp().forEach((id, config) -> {
			if (!config.isEnabled()) {
				infos.add(ServerInfo.deaktiviert(id, config));
				return;
			}
			if (!config.isConfigured()) {
				infos.add(ServerInfo.fehler(id, config, "nicht vollstaendig konfiguriert "
						+ "(STDIO braucht 'command', SSE/HTTP brauchen 'url')"));
				return;
			}
			try {
				McpSyncClient client = connect(id, config);
				List<ToolCallback> tools = SyncMcpToolCallbackProvider.syncToolCallbacks(List.of(client));
				alle.addAll(tools);
				verbunden.add(client);
				infos.add(ServerInfo.verbunden(id, config, tools.size()));
				log.info("MCP-Server '{}' verbunden: {} Tool(s)", id, tools.size());
			}
			catch (RuntimeException ex) {
				infos.add(ServerInfo.fehler(id, config, ex.getMessage()));
				log.warn("MCP-Server '{}' nicht erreichbar: {}", id, ex.getMessage());
			}
		});

		this.callbacks = List.copyOf(alle);
		this.clients = List.copyOf(verbunden);
		this.servers = List.copyOf(infos);
		log.info("{} Tool(s) verfuegbar, {} MCP-Server verbunden", alle.size(), verbunden.size());
		return alle.size();
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
		log.debug("MCP-Server '{}' meldet sich als {}", id, client.getServerInfo());
		return client;
	}

	private void closeClients() {
		clients.forEach(client -> {
			try {
				client.closeGracefully();
			}
			catch (RuntimeException ex) {
				log.warn("MCP-Client liess sich nicht sauber schliessen: {}", ex.getMessage());
			}
		});
		clients = List.of();
	}

	@PreDestroy
	public void shutdown() {
		closeClients();
	}

	/** Alle Tools, die dem Modell angeboten werden. */
	public List<ToolCallback> callbacks() {
		return callbacks;
	}

	/** Beschreibung aller Tools fuer das Frontend. */
	public List<ToolInfo> list() {
		return callbacks.stream()
				.map(callback -> new ToolInfo(
						callback.getToolDefinition().name(),
						callback.getToolDefinition().description(),
						quelle(callback)))
				.toList();
	}

	public List<ServerInfo> servers() {
		return servers;
	}

	/**
	 * Woher stammt das Tool? MCP-Callbacks kommen aus dem Spring-AI-MCP-Paket, alles
	 * andere sind eingebaute Methoden-Tools.
	 */
	private static String quelle(ToolCallback callback) {
		return callback.getClass().getName().startsWith("org.springframework.ai.mcp") ? "mcp" : "builtin";
	}

	/** Ein Tool, wie es dem Modell und dem Frontend gezeigt wird. */
	public record ToolInfo(String name, String description, String source) {
	}

	/** Zustand eines registrierten MCP-Servers. */
	public record ServerInfo(String id, String label, ToolProperties.Transport transport, String target,
			boolean enabled, boolean connected, int toolCount, String error) {

		static ServerInfo verbunden(String id, McpServer config, int toolCount) {
			return new ServerInfo(id, label(id, config), config.getTransport(), target(config), true, true,
					toolCount, null);
		}

		static ServerInfo fehler(String id, McpServer config, String error) {
			return new ServerInfo(id, label(id, config), config.getTransport(), target(config), true, false, 0,
					error);
		}

		static ServerInfo deaktiviert(String id, McpServer config) {
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

	/** Nur zur Diagnose: welche Umgebung bekommt ein stdio-Server mit? */
	Map<String, String> envOf(String serverId) {
		McpServer config = properties.getMcp().get(serverId);
		return config == null ? Map.of() : config.getEnv();
	}
}
