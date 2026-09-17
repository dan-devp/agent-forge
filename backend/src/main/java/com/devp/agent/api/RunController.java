package com.devp.agent.api;

import java.util.List;
import java.util.UUID;

import com.devp.agent.run.AgentRun;
import com.devp.agent.run.AgentService;

import jakarta.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent runs.
 *
 * <p>{@code POST /api/runs} is synchronous: the call returns when the run has finished or
 * exhausted its budget. Progress during the run needs a stream that does not exist yet —
 * until then the budget bounds the waiting time.
 */
@RestController
@RequestMapping("/api/runs")
@Validated
public class RunController {

	private final AgentService agent;

	public RunController(AgentService agent) {
		this.agent = agent;
	}

	@PostMapping
	public AgentRun start(@Validated @RequestBody RunRequest request) {
		return agent.run(request.goal(), request.task(), request.provider(), request.soul(), request.profile());
	}

	/** History without traces. */
	@GetMapping
	public List<AgentRun> history() {
		return agent.history();
	}

	/** One run including its complete trace and scores. */
	@GetMapping("/{id}")
	public AgentRun detail(@PathVariable UUID id) {
		return agent.find(id).orElseThrow(() -> new UnknownRunException("Unknown run '%s'.".formatted(id)));
	}

	/**
	 * Either {@code task} (named task) or {@code goal} (free-form goal); {@code provider},
	 * {@code soul} and {@code profile} override the task's defaults.
	 */
	public record RunRequest(
			@Size(max = 4000, message = "goal is too long (max. 4000 characters)")
			String goal,

			String task,

			String provider,

			String soul,

			String profile) {
	}

	/** The requested run id does not exist. */
	public static class UnknownRunException extends RuntimeException {
		public UnknownRunException(String message) {
			super(message);
		}
	}
}
