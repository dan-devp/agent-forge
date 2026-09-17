package com.devp.agent.eval;

import java.util.ArrayList;
import java.util.List;

import com.devp.agent.run.AgentRun;
import com.devp.agent.run.RunRepository;
import com.devp.agent.run.RunScore;
import com.devp.agent.task.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs every registered grader over a finished run and stores their verdicts. A failing
 * grader stops neither the run nor the other graders — the run is history at this point,
 * only its score would be missing.
 */
@Service
public class EvaluationService {

	private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

	private final List<Grader> graders;
	private final RunRepository runs;

	public EvaluationService(List<Grader> graders, RunRepository runs) {
		this.graders = graders;
		this.runs = runs;
	}

	/** @return the stored scores; empty for a free-form goal or a task without criteria */
	public List<RunScore> evaluate(AgentRun run, Task task) {
		List<RunScore> scores = new ArrayList<>();
		for (Grader grader : graders) {
			try {
				grader.grade(run, task).ifPresent(score -> {
					runs.addScore(run.id(), score);
					scores.add(score);
					log.info("Run {} graded by '{}': {} ({})", run.id(), grader.id(),
							score.passed() ? "passed" : "failed", score.score());
				});
			}
			catch (RuntimeException ex) {
				log.warn("Grader '{}' failed on run {}: {}", grader.id(), run.id(), ex.getMessage());
			}
		}
		return List.copyOf(scores);
	}
}
