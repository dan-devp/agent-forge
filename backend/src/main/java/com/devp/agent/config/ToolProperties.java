package com.devp.agent.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Registered tools.
 *
 * <p>MCP servers are managed by us like the model providers instead of via the Spring AI
 * autoconfiguration: only that way can a server be reconnected at runtime, and the
 * configuration keeps the same shape as {@code app.ai.providers.<id>}.
 */
@ConfigurationProperties(prefix = "app.tools")
public class ToolProperties {

	/** Enable or disable the built-in tools (time, arithmetic). */
	private boolean builtinEnabled = true;

	/**
	 * Fixed instant (ISO-8601, e.g. {@code 2026-09-17T10:00:00Z}) the time tool reports
	 * instead of the wall clock. Empty = wall clock. Needed for reproducible runs against
	 * mocks — two runs must not differ only because the clock moved on.
	 */
	private String fixedTime;

	private Map<String, McpServer> mcp = new LinkedHashMap<>();

	public boolean isBuiltinEnabled() {
		return builtinEnabled;
	}

	public void setBuiltinEnabled(boolean builtinEnabled) {
		this.builtinEnabled = builtinEnabled;
	}

	public String getFixedTime() {
		return fixedTime;
	}

	public void setFixedTime(String fixedTime) {
		this.fixedTime = fixedTime;
	}

	public Map<String, McpServer> getMcp() {
		return mcp;
	}

	public void setMcp(Map<String, McpServer> mcp) {
		this.mcp = mcp;
	}

	/** Transport of an MCP server. */
	public enum Transport {

		/** Local process over stdin/stdout. */
		STDIO,

		/** HTTP with server-sent events. */
		SSE,

		/** Streamable HTTP (MCP from protocol version 2025-03-26). */
		HTTP
	}

	public static class McpServer {

		private String label;

		private Transport transport = Transport.STDIO;

		/** STDIO: program to execute, e.g. {@code npx}. */
		private String command;

		private List<String> args = List.of();

		private Map<String, String> env = new LinkedHashMap<>();

		/** SSE/HTTP: base URL of the server. */
		private String url;

		/** Timeout for connecting and per call. */
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

		/** Usably configured? STDIO needs a command, SSE/HTTP a URL. */
		public boolean isConfigured() {
			return transport == Transport.STDIO
					? command != null && !command.isBlank()
					: url != null && !url.isBlank();
		}
	}
}
