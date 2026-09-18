package com.devp.agent.training;

import java.util.List;
import java.util.UUID;

import com.devp.agent.strategy.MemoryStore;

/**
 * The {@link MemoryStore} of one run inside one training: reads the training's entries,
 * writes attributed to this run.
 */
public final class TrainingMemoryStore implements MemoryStore {

	private final TrainingRepository repository;
	private final UUID trainingId;
	private final UUID runId;

	public TrainingMemoryStore(TrainingRepository repository, UUID trainingId, UUID runId) {
		this.repository = repository;
		this.trainingId = trainingId;
		this.runId = runId;
	}

	@Override
	public List<TrainingMemory> read(String strategyId) {
		return repository.memory(trainingId, strategyId);
	}

	@Override
	public List<TrainingMemory> read(String strategyId, String kind) {
		return repository.memory(trainingId, strategyId, kind);
	}

	@Override
	public void write(String strategyId, String kind, String content) {
		if (content == null || content.isBlank()) {
			return;
		}
		repository.remember(trainingId, strategyId, runId, kind, content.strip());
	}

	@Override
	public boolean persistent() {
		return true;
	}
}
