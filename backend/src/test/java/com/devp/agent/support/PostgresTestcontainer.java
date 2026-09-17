package com.devp.agent.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real Postgres instance for tests. The migration is Postgres-specific — a different
 * dialect in tests would hide exactly the errors that matter.
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
