package com.devp.agent.eval;

import java.util.Optional;

import com.devp.agent.run.AgentRun;
import com.devp.agent.run.RunScore;
import com.devp.agent.task.Task;

/**
 * Judges a finished run against its task.
 *
 * <p>A grader is never the run's own agent. Further implementations (a judge agent with its
 * own SOUL and a different provider, a human via the API) write to the same table and are
 * kept side by side.
 */
public interface Grader {

	/** Identifier under which the score is stored. */
	String id();

	/**
	 * @return empty if this grader has nothing to say about the run or task — for instance
	 *         because the task carries no criteria of its kind
	 */
	Optional<RunScore> grade(AgentRun run, Task task);
}
