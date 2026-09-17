package com.devp.agent.api;

import java.util.List;
import java.util.UUID;

import com.devp.agent.run.AgentRun;
import com.devp.agent.series.Series;
import com.devp.agent.series.SeriesService;
import com.devp.agent.series.SeriesStatistics;

import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Series: one task, N runs, statistics.
 *
 * <p>{@code POST} answers 202 at once with the created series; the runs happen in the
 * background. Poll {@code GET /api/series/{id}} until {@code status} is no longer RUNNING.
 */
@RestController
@RequestMapping("/api/series")
@Validated
public class SeriesController {

	private final SeriesService series;

	public SeriesController(SeriesService series) {
		this.series = series;
	}

	@PostMapping
	public ResponseEntity<Series> start(@Validated @RequestBody SeriesRequest request) {
		Series created = series.start(request.task(), request.repetitions() == null ? 5 : request.repetitions(),
				request.provider(), request.soul(), request.profile());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(created);
	}

	@GetMapping
	public List<Series> list() {
		return series.list();
	}

	/** Series, statistics computed now, and its runs without traces. */
	@GetMapping("/{id}")
	public SeriesDetail detail(@PathVariable UUID id) {
		Series s = series.find(id).orElseThrow(() -> new UnknownSeriesException("Unknown series '%s'.".formatted(id)));
		List<AgentRun> runs = series.runsOf(id);
		return new SeriesDetail(s, series.statistics(s, runs), runs);
	}

	/** Only a named task can be repeated; {@code repetitions} defaults to 5. */
	public record SeriesRequest(
			@NotBlank(message = "task is required — only a named task can be repeated")
			String task,

			Integer repetitions,

			String provider,

			String soul,

			String profile) {
	}

	public record SeriesDetail(Series series, SeriesStatistics statistics, List<AgentRun> runs) {
	}

	/** The requested series id does not exist. */
	public static class UnknownSeriesException extends RuntimeException {
		public UnknownSeriesException(String message) {
			super(message);
		}
	}
}
