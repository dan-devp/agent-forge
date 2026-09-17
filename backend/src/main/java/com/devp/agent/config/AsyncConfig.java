package com.devp.agent.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Background execution of series.
 *
 * <p>One thread on purpose: series run one after another, and within a series the runs are
 * sequential. Budgets, mock logs and traces stay readable, and a local model is not asked to
 * serve many requests at once. Parallelism is a later, deliberate decision.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

	@Bean(name = "seriesExecutor")
	public Executor seriesExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("series-");
		executor.initialize();
		return executor;
	}
}
