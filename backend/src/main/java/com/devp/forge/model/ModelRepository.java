package com.devp.forge.model;

import com.devp.forge.config.DataProperties;
import com.devp.forge.store.FileStore;

import org.springframework.stereotype.Repository;

/** {@code files/models/<id>/v<n>.md} */
@Repository
public class ModelRepository extends FileStore<Model> {

	public ModelRepository(DataProperties data) {
		super(data.filesDir(), new ModelCodec(), data.isSeed());
	}
}
