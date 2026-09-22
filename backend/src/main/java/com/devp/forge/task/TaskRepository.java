package com.devp.forge.task;

import com.devp.forge.config.DataProperties;
import com.devp.forge.eval.EvalRegistry;
import com.devp.forge.store.FileStore;

import org.springframework.stereotype.Repository;

/** {@code files/tasks/<id>/v<n>.md} — every eval entry is checked against the registry on load and save. */
@Repository
public class TaskRepository extends FileStore<Task> {

	public TaskRepository(DataProperties data, EvalRegistry evals) {
		super(data.filesDir(), new TaskCodec(evals), data.isSeed());
	}
}
