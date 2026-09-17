package com.devp.agent;

import com.devp.agent.support.PostgresTestcontainer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestcontainer.class)
class AgentApplicationTests {

	@Test
	void contextLoads() {
	}

}
