package com.devp.agent.tools;

import java.util.List;
import java.util.Map;

import com.devp.agent.config.ToolProperties;
import com.devp.agent.config.ToolProperties.McpServer;
import com.devp.agent.config.ToolProperties.Transport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryTests {

	private static ToolRegistry registry(ToolProperties properties) {
		return new ToolRegistry(properties, new BuiltinTools(new ToolProperties()));
	}

	@Test
	void offersBuiltinTools() {
		ToolRegistry registry = registry(new ToolProperties());

		assertThat(registry.list()).extracting(ToolRegistry.ToolInfo::name)
				.contains("currentTime", "divide");
		assertThat(registry.list()).allMatch(t -> t.source().equals("builtin"));
	}

	@Test
	void allowsDisablingBuiltinTools() {
		ToolProperties properties = new ToolProperties();
		properties.setBuiltinEnabled(false);

		assertThat(registry(properties).callbacks()).isEmpty();
	}

	/** A disabled server is listed but not connected. */
	@Test
	void listsDisabledServers() {
		ToolProperties properties = new ToolProperties();
		McpServer server = new McpServer();
		server.setLabel("Files");
		server.setTransport(Transport.STDIO);
		server.setCommand("npx");
		server.setEnabled(false);
		properties.setMcp(Map.of("files", server));

		ToolRegistry.ServerInfo info = registry(properties).servers().getFirst();
		assertThat(info.id()).isEqualTo("files");
		assertThat(info.label()).isEqualTo("Files");
		assertThat(info.enabled()).isFalse();
		assertThat(info.connected()).isFalse();
	}

	/** Incomplete configuration is reported, not connected — and breaks nothing. */
	@Test
	void reportsIncompleteConfiguration() {
		ToolProperties properties = new ToolProperties();
		McpServer server = new McpServer();
		server.setTransport(Transport.SSE);
		properties.setMcp(Map.of("remote", server));

		ToolRegistry.ServerInfo info = registry(properties).servers().getFirst();
		assertThat(info.connected()).isFalse();
		assertThat(info.error()).contains("not fully configured");
	}

	/**
	 * An unreachable server must not prevent startup: the built-in tools stay usable, the
	 * error stays visible.
	 */
	@Test
	void survivesUnreachableServer() {
		ToolProperties properties = new ToolProperties();
		McpServer server = new McpServer();
		server.setTransport(Transport.SSE);
		server.setUrl("http://localhost:1/nothing");
		server.setTimeoutMs(1500);
		properties.setMcp(Map.of("dead", server));

		ToolRegistry registry = registry(properties);

		assertThat(registry.servers()).hasSize(1);
		assertThat(registry.servers().getFirst().connected()).isFalse();
		assertThat(registry.servers().getFirst().error()).isNotBlank();
		assertThat(registry.list()).extracting(ToolRegistry.ToolInfo::name).contains("currentTime");
	}

	@Test
	void reloadReturnsTheNumberOfTools() {
		ToolRegistry registry = registry(new ToolProperties());

		assertThat(registry.reload()).isEqualTo(registry.callbacks().size());
	}

	@Test
	void divideReportsDivisionByZero() {
		assertThat(new BuiltinTools(new ToolProperties()).divide(10, 0)).contains("Division durch 0");
		assertThat(new BuiltinTools(new ToolProperties()).divide(100, 7)).isEqualTo("100 geteilt durch 7 ergibt 14 Rest 2");
	}

	@Test
	void currentTimeAcceptsTimeZones() {
		assertThat(new BuiltinTools(new ToolProperties()).currentTime("UTC")).isNotBlank();
		assertThat(new BuiltinTools(new ToolProperties()).currentTime(null)).isNotBlank();
	}

	/** Reproducible runs need a clock that does not move — the wall clock differs every time. */
	@Test
	void currentTimeCanBeFixed() {
		ToolProperties properties = new ToolProperties();
		properties.setFixedTime("2026-09-17T10:00:00Z");

		BuiltinTools tools = new BuiltinTools(properties);

		assertThat(tools.currentTime("UTC")).isEqualTo("2026-09-17 10:00:00 (UTC)");
		assertThat(tools.currentTime("Europe/Berlin")).startsWith("2026-09-17 12:00:00");
		assertThat(tools.currentTime("UTC")).isEqualTo(tools.currentTime("UTC"));
	}

	@Test
	void blankFixedTimeMeansWallClock() {
		ToolProperties properties = new ToolProperties();
		properties.setFixedTime("  ");

		assertThat(new BuiltinTools(properties).currentTime("UTC")).isNotBlank();
	}

	@Test
	void serverListIsEmptyWithoutServers() {
		assertThat(registry(new ToolProperties()).servers()).isEqualTo(List.of());
	}
}
