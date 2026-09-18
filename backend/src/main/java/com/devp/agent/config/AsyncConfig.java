package com.devp.agent.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Background execution of trainings.
 *
 * <p>One thread on purpose: trainings run one after another, and within a training the runs
 * are sequential — run k must be able to read what runs 1..k-1 remembered. Budgets, mock logs
 * and traces stay readable, and a local model is not asked to serve many requests at once.
 * Parallelism across trainings is a later, deliberate decision.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

	@Bean(name = "trainingExecutor")
	public Executor trainingExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("training-");
		executor.initialize();
		return executor;
	}
}
