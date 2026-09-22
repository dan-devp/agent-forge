package com.devp.forge.eval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.devp.forge.run.Run;
import com.devp.forge.run.RunEval;
import com.devp.forge.run.RunRepository;
import com.devp.forge.run.RunStep;
import com.devp.forge.run.StepType;
import com.devp.forge.task.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs every eval of the task against the finished run and writes the verdicts.
 *
 * <p>Every final status is evaluated, budget and failure included: a run without an answer
 * fails its evals, and exactly that belongs in the statistics. One broken eval is recorded
 * as failed with its error, and the others still run.
 */
@Service
public class EvaluationService {

	private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

	private final EvalRegistry registry;
	private final RunRepository runs;

	public EvaluationService(EvalRegistry registry, RunRepository runs) {
		this.registry = registry;
		this.runs = runs;
	}

	/** @return the run with its verdicts and aggregate {@code passed}; unchanged when there is nothing to evaluate */
	public Run evaluate(Run run, Task task) {
		if (task == null || task.evals().isEmpty()) {
			return run;
		}

		List<String> calledTools = run.trace().stream()
				.filter(step -> step.type() == StepType.TOOL)
				.map(RunStep::name)
				.toList();
		EvalSubject subject = new EvalSubject(run.input(), run.answer(), run.status().name(), calledTools);

		List<RunEval> verdicts = new ArrayList<>();
		int index = 0;
		for (EvalConfig config : task.evals()) {
			index++;
			EvalResult result;
			try {
				result = registry.get(config.type()).evaluate(config, subject);
			}
			catch (RuntimeException ex) {
				log.warn("Eval #{} ({}) of run {} failed: {}", index, config.type(), run.id(), ex.getMessage());
				result = EvalResult.fail("Eval fehlgeschlagen: " + ex.getMessage());
			}
			RunEval verdict = new RunEval(index, config.type(), result.passed(), result.rationale(), result.model(),
					result.tokens(), result.durationMs(), Instant.now());
			runs.addEval(run.id(), verdict);
			verdicts.add(verdict);
		}

		Run evaluated = run.withEvals(verdicts);
		runs.setPassed(run.id(), evaluated.passed());
		log.info("Run {} evaluated: {} of {} eval(s) passed", run.id(),
				verdicts.stream().filter(RunEval::passed).count(), verdicts.size());
		return evaluated;
	}
}
