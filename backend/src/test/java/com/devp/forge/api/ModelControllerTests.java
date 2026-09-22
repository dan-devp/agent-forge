package com.devp.forge.api;

import com.devp.forge.model.ModelRepository;
import com.devp.forge.support.PostgresTestcontainer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The write path end to end: request in, file on disk, new version out, errors as ProblemDetail. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class ModelControllerTests {

	private static final String ID = "ctl-" + Long.toHexString(System.nanoTime());

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ModelRepository models;

	@AfterEach
	void cleanUp() {
		if (models.exists(ID)) {
			models.archive(ID);
		}
	}

	private static String body(String name, String apiKeyEnv, boolean local) {
		return """
				{"id":"%s","name":"%s","provider":"%s","baseUrl":"https://gw.example.com/v1","modelId":"qwen",
				 "apiKeyEnv":%s,"temperature":0.3}
				""".formatted(ID, name, local ? "local" : "custom",
				apiKeyEnv == null ? "null" : "\"" + apiKeyEnv + "\"");
	}

	@Test
	void createsListsUpdatesAndArchives() throws Exception {
		mvc.perform(post("/api/models").contentType(MediaType.APPLICATION_JSON).content(body("Qwen", "API_KEY_GW", false)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.apiKeyEnv").value("API_KEY_GW"))
				// The variable is not set in tests: the model says so, and never shows a value.
				.andExpect(jsonPath("$.keySet").value(false))
				.andExpect(jsonPath("$.configured").value(false))
				.andExpect(jsonPath("$.problem").value(containsString("API_KEY_GW")));

		mvc.perform(get("/api/models"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(hasItem(ID)));

		mvc.perform(put("/api/models/" + ID).contentType(MediaType.APPLICATION_JSON).content(body("Qwen 2", null, true)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(2))
				.andExpect(jsonPath("$.name").value("Qwen 2"))
				.andExpect(jsonPath("$.local").value(true))
				.andExpect(jsonPath("$.configured").value(true));

		mvc.perform(get("/api/models/" + ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.model.version").value(2))
				.andExpect(jsonPath("$.versions[0]").value(1))
				.andExpect(jsonPath("$.versions[1]").value(2))
				.andExpect(jsonPath("$.usedByAgents").isEmpty());

		mvc.perform(get("/api/models/" + ID + "/versions/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Qwen"));

		mvc.perform(get("/api/models/" + ID + "/versions/1/source"))
				.andExpect(status().isOk())
				.andExpect(content(containsString("api-key-env: API_KEY_GW")));

		mvc.perform(delete("/api/models/" + ID)).andExpect(status().isNoContent());
		mvc.perform(get("/api/models/" + ID)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Not found"));
	}

	@Test
	void duplicateIdIsAConflict() throws Exception {
		mvc.perform(post("/api/models").contentType(MediaType.APPLICATION_JSON).content(body("A", null, true)))
				.andExpect(status().isCreated());
		mvc.perform(post("/api/models").contentType(MediaType.APPLICATION_JSON).content(body("B", null, true)))
				.andExpect(status().isConflict());
	}

	@Test
	void invalidModelIsABadRequestNamingTheField() throws Exception {
		String noUrl = """
				{"id":"%s","name":"X","provider":"local","baseUrl":"ftp://nope","modelId":"m"}
				""".formatted(ID);
		mvc.perform(post("/api/models").contentType(MediaType.APPLICATION_JSON).content(noUrl))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("base-url")));
	}

	/** A cloud provider fixes the URL and fills it in when omitted; a different URL is refused. */
	@Test
	void cloudProviderFixesTheBaseUrl() throws Exception {
		String openai = """
				{"id":"%s","name":"GPT","provider":"openai","modelId":"gpt-4o-mini","apiKeyEnv":"API_KEY_OPENAI"}
				""".formatted(ID);
		mvc.perform(post("/api/models").contentType(MediaType.APPLICATION_JSON).content(openai))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.provider").value("openai"))
				.andExpect(jsonPath("$.baseUrl").value("https://api.openai.com/v1"));

		String elsewhere = """
				{"name":"GPT","provider":"openai","baseUrl":"https://evil.example/v1","modelId":"x","apiKeyEnv":"API_KEY_OPENAI"}
				""";
		mvc.perform(put("/api/models/" + ID).contentType(MediaType.APPLICATION_JSON).content(elsewhere))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("must be https://api.openai.com/v1")));

		String noKey = """
				{"name":"GPT","provider":"openai","modelId":"x"}
				""";
		mvc.perform(put("/api/models/" + ID).contentType(MediaType.APPLICATION_JSON).content(noKey))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("api-key-env")));
	}

	/** The seeded model is referenced by the seeded agents — archiving it must be refused. */
	@Test
	void modelInUseCannotBeArchived() throws Exception {
		mvc.perform(delete("/api/models/local"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value(containsString("forge")));
	}

	private static org.springframework.test.web.servlet.ResultMatcher content(org.hamcrest.Matcher<String> matcher) {
		return org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(matcher);
	}
}
