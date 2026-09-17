package com.devp.agent.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Echte Postgres-Instanz fuer Tests. Die Migration ist postgres-spezifisch — ein
 * anderer Dialekt im Test wuerde genau die Fehler verstecken, die zaehlen.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer("pgvector/pgvector:pg18")
				.withDatabaseName("agent")
				.withUsername("agent")
				.withPassword("agent");
	}
}
