package com.devp.agent.strategy;

import java.util.List;

import com.devp.agent.training.TrainingMemory;

/**
 * What a strategy may remember across the runs of one training.
 *
 * <p>Scoped twice: to the training (training A never sees training B) and to the strategy
 * (a strategy reads only what it wrote itself, unless it asks for another id on purpose).
 * Every entry records the run it came from, so a learning curve can be explained.
 *
 * <p>Outside a training there is nothing to remember into: single runs get a store that
 * reads empty and drops writes — a strategy must not fail because it was run alone.
 */
public interface MemoryStore {

	/** Entries of this strategy in this training, oldest first. */
	List<TrainingMemory> read(String strategyId);

	/** Entries of this strategy of one kind, oldest first. */
	List<TrainingMemory> read(String strategyId, String kind);

	/** Remembers something for the rest of this training; attributed to the current run. */
	void write(String strategyId, String kind, String content);

	/** Does anything persist here at all? False for a single run outside a training. */
	boolean persistent();

	/** The store of a single run: nothing in, nothing out. */
	MemoryStore NONE = new MemoryStore() {

		@Override
		public List<TrainingMemory> read(String strategyId) {
			return List.of();
		}

		@Override
		public List<TrainingMemory> read(String strategyId, String kind) {
			return List.of();
		}

		@Override
		public void write(String strategyId, String kind, String content) {
		}

		@Override
		public boolean persistent() {
			return false;
		}
	};
}
