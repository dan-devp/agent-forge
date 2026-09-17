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
		return new ToolRegistry(properties, new BuiltinTools());
	}

	@Test
	void bietetEingebauteToolsAn() {
		ToolRegistry registry = registry(new ToolProperties());

		assertThat(registry.list()).extracting(ToolRegistry.ToolInfo::name)
				.contains("aktuelleZeit", "teilen");
		assertThat(registry.list()).allMatch(t -> t.source().equals("builtin"));
	}

	@Test
	void laesstEingebauteToolsAbschalten() {
		ToolProperties properties = new ToolProperties();
		properties.setBuiltinEnabled(false);

		assertThat(registry(properties).callbacks()).isEmpty();
	}

	/** Ein deaktivierter Server wird gefuehrt, aber nicht verbunden. */
	@Test
	void fuehrtDeaktivierteServer() {
		ToolProperties properties = new ToolProperties();
		McpServer server = new McpServer();
		server.setLabel("Dateien");
		server.setTransport(Transport.STDIO);
		server.setCommand("npx");
		server.setEnabled(false);
		properties.setMcp(Map.of("files", server));

		ToolRegistry.ServerInfo info = registry(properties).servers().getFirst();
		assertThat(info.id()).isEqualTo("files");
		assertThat(info.label()).isEqualTo("Dateien");
		assertThat(info.enabled()).isFalse();
		assertThat(info.connected()).isFalse();
	}

	/** Unvollstaendige Konfiguration wird gemeldet, nicht verbunden — und bricht nichts ab. */
	@Test
	void meldetUnvollstaendigeKonfiguration() {
		ToolProperties properties = new ToolProperties();
		McpServer server = new McpServer();
		server.setTransport(Transport.SSE);
		properties.setMcp(Map.of("remote", server));

		ToolRegistry.ServerInfo info = registry(properties).servers().getFirst();
		assertThat(info.connected()).isFalse();
		assertThat(info.error()).contains("nicht vollstaendig konfiguriert");
	}

	/**
	 * Ein nicht erreichbarer Server darf den Start nicht verhindern: die eingebauten
	 * Tools bleiben nutzbar, der Fehler bleibt sichtbar.
	 */
	@Test
	void ueberlebtNichtErreichbarenServer() {
		ToolProperties properties = new ToolProperties();
		McpServer server = new McpServer();
		server.setTransport(Transport.SSE);
		server.setUrl("http://localhost:1/nichts");
		server.setTimeoutMs(1500);
		properties.setMcp(Map.of("tot", server));

		ToolRegistry registry = registry(properties);

		assertThat(registry.servers()).hasSize(1);
		assertThat(registry.servers().getFirst().connected()).isFalse();
		assertThat(registry.servers().getFirst().error()).isNotBlank();
		assertThat(registry.list()).extracting(ToolRegistry.ToolInfo::name).contains("aktuelleZeit");
	}

	@Test
	void reloadLiefertDieAnzahlDerTools() {
		ToolRegistry registry = registry(new ToolProperties());

		assertThat(registry.reload()).isEqualTo(registry.callbacks().size());
	}

	@Test
	void teilenMeldetDivisionDurchNull() {
		assertThat(new BuiltinTools().teilen(10, 0)).contains("Division durch 0");
		assertThat(new BuiltinTools().teilen(100, 7)).isEqualTo("100 geteilt durch 7 ergibt 14 Rest 2");
	}

	@Test
	void aktuelleZeitAkzeptiertZeitzonen() {
		assertThat(new BuiltinTools().aktuelleZeit("UTC")).isNotBlank();
		assertThat(new BuiltinTools().aktuelleZeit(null)).isNotBlank();
	}

	@Test
	void ohneServerIstDieListeLeer() {
		assertThat(registry(new ToolProperties()).servers()).isEqualTo(List.of());
	}
}
