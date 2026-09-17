package com.devp.agent.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Registrierte Werkzeuge.
 *
 * <p>MCP-Server werden wie die Modell-Provider selbst verwaltet statt ueber die
 * Spring-AI-Autoconfiguration: nur so laesst sich ein Server zur Laufzeit neu laden,
 * und die Konfigurationsform bleibt dieselbe wie bei {@code app.ai.providers.<id>}.
 */
@ConfigurationProperties(prefix = "app.tools")
public class ToolProperties {

	/** Eingebaute Tools (Zeit, Rechnen) an- oder abschalten. */
	private boolean builtinEnabled = true;

	private Map<String, McpServer> mcp = new LinkedHashMap<>();

	public boolean isBuiltinEnabled() {
		return builtinEnabled;
	}

	public void setBuiltinEnabled(boolean builtinEnabled) {
		this.builtinEnabled = builtinEnabled;
	}

	public Map<String, McpServer> getMcp() {
		return mcp;
	}

	public void setMcp(Map<String, McpServer> mcp) {
		this.mcp = mcp;
	}

	/** Transportart eines MCP-Servers. */
	public enum Transport {

		/** Lokaler Prozess ueber stdin/stdout. */
		STDIO,

		/** HTTP mit Server-Sent-Events. */
		SSE,

		/** Streamable HTTP (MCP ab Protokollversion 2025-03-26). */
		HTTP
	}

	public static class McpServer {

		private String label;

		private Transport transport = Transport.STDIO;

		/** STDIO: auszufuehrendes Programm, z. B. {@code npx}. */
		private String command;

		private List<String> args = List.of();

		private Map<String, String> env = new LinkedHashMap<>();

		/** SSE/HTTP: Basis-URL des Servers. */
		private String url;

		/** Wartezeit beim Verbindungsaufbau und je Aufruf. */
		private long timeoutMs = 20_000;

		private boolean enabled = true;

		public String getLabel() {
			return label;
		}

		public void setLabel(String label) {
			this.label = label;
		}

		public Transport getTransport() {
			return transport;
		}

		public void setTransport(Transport transport) {
			this.transport = transport;
		}

		public String getCommand() {
			return command;
		}

		public void setCommand(String command) {
			this.command = command;
		}

		public List<String> getArgs() {
			return args;
		}

		public void setArgs(List<String> args) {
			this.args = args;
		}

		public Map<String, String> getEnv() {
			return env;
		}

		public void setEnv(Map<String, String> env) {
			this.env = env;
		}

		public String getUrl() {
			return url;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public long getTimeoutMs() {
			return timeoutMs;
		}

		public void setTimeoutMs(long timeoutMs) {
			this.timeoutMs = timeoutMs;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		/** Nutzbar konfiguriert? STDIO braucht ein Kommando, SSE/HTTP eine URL. */
		public boolean isConfigured() {
			return transport == Transport.STDIO
					? command != null && !command.isBlank()
					: url != null && !url.isBlank();
		}
	}
}
