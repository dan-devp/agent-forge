package com.devp.forge.api;

import java.util.List;
import java.util.UUID;

import com.devp.forge.run.Run;
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

/** Runs: start one (synchronous — the response is the finished run), list, inspect. */
@RestController
@RequestMapping("/api/runs")
public class RunController {

	private final RunService service;

	public RunController(RunService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Run start(@RequestBody RunRequest request) {
		return service.run(request.agent(), request.task(), request.input());
	}

	@GetMapping
	public List<Run> list(@RequestParam(required = false) String task, @RequestParam(required = false) String agent) {
		return service.history(task, agent);
	}

	@GetMapping("/{id}")
	public Run get(@PathVariable UUID id) {
		return service.find(id).orElseThrow(() -> new UnknownRunException(id));
	}

	/** Either {@code task} or {@code input}; {@code agent} always. */
	public record RunRequest(String agent, String task, String input) {
	}

	public static class UnknownRunException extends RuntimeException {
		public UnknownRunException(UUID id) {
			super("No run with id " + id);
		}
	}
}
