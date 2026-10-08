package com.devp.forge.training;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.devp.forge.agent.Agent;
import com.devp.forge.agent.AgentRepository;
import com.devp.forge.agent.ToolAccess;
import com.devp.forge.config.TrainingProperties;
import com.devp.forge.run.RunRepository;
import com.devp.forge.run.RunStatus;
import com.devp.forge.support.PostgresTestcontainer;
import com.devp.forge.training.Bench.Measurement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The training files through the API, the in-use guards, and the real bench against the dead
 * test endpoint — every run fails at once, which is exactly what it has to count.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class TrainingApiTests {

	@Autowired private MockMvc mvc;
	@Autowired private AgentRepository agents;
	@Autowired private TrainingRepository trainings;
	@Autowired private QueueBench bench;
	@Autowired private RunRepository runs;
	@Autowired private TrainingProperties properties;

	private final String suffix = Long.toHexString(System.nanoTime());
	private final String agentId = "api-trainee-" + suffix;
	private final String coachId = "api-coach-" + suffix;
	private final String trainingId = "api-tr-" + suffix;

	@BeforeEach
	void setUp() {
		agents.create(new Agent(agentId, 0, "Trainee", null, "local", ToolAccess.ALL, "You are Trainee."));
		agents.create(new Agent(coachId, 0, "Coach", null, "local", ToolAccess.NONE, "You are Coach."));
	}

	@AfterEach
	void cleanUp() {
		if (trainings.exists(trainingId)) trainings.archive(trainingId);
		for (String id : List.of(agentId, coachId)) {
			if (agents.exists(id)) agents.archive(id);
		}
	}

	private String body(String agent, String train) {
		return """
				{"id":"%s","name":"Training","agent":"%s","coach":"%s","train":["%s"],"holdout":["no-tool"],
				 "repetitions":2,"maxSteps":2,"maxTokens":1000,"approval":"manual","goal":"Tools nutzen."}
				""".formatted(trainingId, agent, coachId, train);
	}

	@Test
	void createsUpdatesAndGuardsWhatATrainingUses() throws Exception {
		mvc.perform(post("/api/trainings").contentType(MediaType.APPLICATION_JSON).content(body("nobody", "time-and-division")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("agent 'nobody'")));
		mvc.perform(post("/api/trainings").contentType(MediaType.APPLICATION_JSON).content(body(agentId, "no-such-task")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("task 'no-such-task'")));

		mvc.perform(post("/api/trainings").contentType(MediaType.APPLICATION_JSON).content(body(agentId, "time-and-division")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.approval").value("MANUAL"))
				.andExpect(jsonPath("$.goal").value("Tools nutzen."));
		mvc.perform(put("/api/trainings/" + trainingId).contentType(MediaType.APPLICATION_JSON)
				.content(body(agentId, "time-and-division").replace("\"maxSteps\":2", "\"maxSteps\":5")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(2))
				.andExpect(jsonPath("$.maxSteps").value(5));
		mvc.perform(get("/api/trainings/" + trainingId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.versions.length()").value(2))
				.andExpect(jsonPath("$.sessions.length()").value(0));
		mvc.perform(get("/api/trainings/" + trainingId + "/versions/2/source"))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
						.string(containsString("approval: manual")));

		// A start version the agent does not have is refused before anything runs.
		mvc.perform(post("/api/trainings/" + trainingId + "/sessions").contentType(MediaType.APPLICATION_JSON)
				.content("{\"startVersion\": 99}")).andExpect(status().isNotFound());

		// What a training uses cannot be archived under it.
		mvc.perform(delete("/api/agents/" + coachId)).andExpect(status().isConflict());
		mvc.perform(delete("/api/tasks/no-tool")).andExpect(status().isConflict());

		mvc.perform(delete("/api/trainings/" + trainingId)).andExpect(status().isNoContent());
		mvc.perform(get("/api/sessions/00000000-0000-0000-0000-000000000000")).andExpect(status().isNotFound());
	}

	@Test
	void theRealBenchCountsFailedRunsAndShowsOnlyTrainFailures() {
		properties.setPollMs(50);
		AtomicReference<UUID> started = new AtomicReference<>();
		Measurement m = bench.measure(agentId, 1, List.of("time-and-division"), List.of("no-tool"), 2, () -> false,
				started::set);

		assertThat(m.score()).isEqualTo(new Score(0, 2, 0, 2));
		assertThat(m.tasks()).extracting(Bench.TaskScore::taskId).containsExactly("time-and-division", "no-tool");
		assertThat(m.failures()).hasSize(2).allSatisfy(f -> {
			assertThat(f.taskId()).isEqualTo("time-and-division");
			assertThat(f.status()).isEqualTo(RunStatus.FAILED.name());
			assertThat(f.error()).isNotBlank();
		});
		assertThat(runs.list(10, null, null, m.batchId())).hasSize(4);
		assertThat(started.get()).isEqualTo(m.batchId());
	}
}
