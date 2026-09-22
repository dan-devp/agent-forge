package com.devp.forge;

import com.devp.forge.support.PostgresTestcontainer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestcontainer.class)
class ForgeApplicationTests {

	@Test
	void contextLoads() {
	}

}
