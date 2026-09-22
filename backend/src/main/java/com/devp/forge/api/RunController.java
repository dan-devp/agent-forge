package com.devp.forge.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.devp.forge.run.Run;
import com.devp.forge.run.RunQueue;
import com.devp.forge.run.RunQueue.Batch;
import com.devp.forge.run.RunQueue.Matrix;
import com.devp.forge.run.RunService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs. One synchronous path for trying things out, one queued path for measuring:
 * a batch is agent version x tasks x repetitions, executed one after another.
 */
@RestController
@RequestMapping("/api/runs")
public class RunController {

	private final RunService service;
	private final RunQueue queue;

	public RunController(RunService service, RunQueue queue) {
		this.service = service;
		this.queue = queue;
	}

	/** Synchronous — the response is the finished run. Either {@code task} or {@code input}. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Run start(@RequestBody RunRequest request) {
		return service.run(request.agent(), request.agentVersion(), request.task(), request.input());
	}

	/** Queued — 202 with the batch and its run ids; poll the runs or the matrix. */
	@PostMapping("/batch")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public Batch batch(@RequestBody BatchRequest request) {
		return queue.enqueue(request.agent(), request.agentVersion(), request.tasks(),
				request.repetitions() == null ? 1 : request.repetitions());
	}

	@GetMapping("/batch/{batchId}")
	public List<Run> batchRuns(@PathVariable UUID batchId) {
		return service.history(null, null, batchId);
	}

	@PostMapping("/batch/{batchId}/cancel")
	public Map<String, Integer> cancelBatch(@PathVariable UUID batchId) {
		return Map.of("cancelled", queue.cancelBatch(batchId));
	}

	@PostMapping("/{id}/cancel")
	public Map<String, Boolean> cancel(@PathVariable UUID id) {
		service.find(id).orElseThrow(() -> new UnknownRunException(id));
		return Map.of("cancelled", queue.cancel(id));
	}

	@GetMapping
	public List<Run> list(@RequestParam(required = false) String task, @RequestParam(required = false) String agent) {
		return service.history(task, agent, null);
	}

	/** Agent versions x tasks, each cell the runs of that pair. */
	@GetMapping("/matrix")
	public Matrix matrix(@RequestParam String agent) {
		return queue.matrix(agent);
	}

	@GetMapping("/{id}")
	public Run get(@PathVariable UUID id) {
		return service.find(id).orElseThrow(() -> new UnknownRunException(id));
	}

	/** Either {@code task} or {@code input}; {@code agent} always; {@code agentVersion} optional. */
	public record RunRequest(String agent, Integer agentVersion, String task, String input) {
	}

	/** {@code tasks} empty or absent = every current task. */
	public record BatchRequest(String agent, Integer agentVersion, List<String> tasks, Integer repetitions) {
	}

	public static class UnknownRunException extends RuntimeException {
		public UnknownRunException(UUID id) {
			super("No run with id " + id);
		}
	}
}
