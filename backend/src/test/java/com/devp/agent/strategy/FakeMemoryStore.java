package com.devp.agent.strategy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.devp.agent.training.TrainingMemory;

/** In-memory stand-in for the training store — the strategies under test only need read and write. */
class FakeMemoryStore implements MemoryStore {

	private final List<TrainingMemory> entries = new ArrayList<>();

	private final UUID runId = UUID.randomUUID();

	@Override
	public List<TrainingMemory> read(String strategyId) {
		return entries.stream().filter(e -> e.strategyId().equals(strategyId)).toList();
	}

	@Override
	public List<TrainingMemory> read(String strategyId, String kind) {
		return read(strategyId).stream().filter(e -> e.kind().equals(kind)).toList();
	}

	@Override
	public void write(String strategyId, String kind, String content) {
		entries.add(new TrainingMemory(entries.size() + 1L, UUID.randomUUID(), strategyId, runId, kind, content,
				Instant.now()));
	}

	@Override
	public boolean persistent() {
		return true;
	}

	List<String> contents() {
		return entries.stream().map(TrainingMemory::content).toList();
	}
}
