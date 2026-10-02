package com.devp.forge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Runs execute one after another on a single thread. Parallel runs would make the load at
 * the endpoint an unrecorded variable in every comparison — and a queue of one keeps the
 * cost ceiling where the batch limit put it.
 */
@Configuration
public class AsyncConfig {

	@Bean(name = "runExecutor")
	public ThreadPoolTaskExecutor runExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(Integer.MAX_VALUE);
		executor.setThreadNamePrefix("run-");
		executor.setWaitForTasksToCompleteOnShutdown(false);
		executor.initialize();
		return executor;
	}

	/**
	 * A training session mostly waits — for its batch on the run executor, or for a human.
	 * It gets its own thread so it never blocks the runs it waits for. One at a time: two
	 * sessions would share the run queue and slow each other's measurements.
	 */
	@Bean(name = "trainingExecutor")
	public ThreadPoolTaskExecutor trainingExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(0);
		executor.setThreadNamePrefix("training-");
		executor.setWaitForTasksToCompleteOnShutdown(false);
		executor.initialize();
		return executor;
	}
}
