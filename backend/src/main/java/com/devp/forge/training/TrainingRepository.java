package com.devp.forge.training;

import com.devp.forge.config.DataProperties;
import com.devp.forge.store.FileStore;

import org.springframework.stereotype.Repository;

/** {@code files/trainings/<id>/v<n>.md} */
@Repository
public class TrainingRepository extends FileStore<Training> {

	public TrainingRepository(DataProperties data) {
		super(data.filesDir(), new TrainingCodec(), data.isSeed());
	}
}
