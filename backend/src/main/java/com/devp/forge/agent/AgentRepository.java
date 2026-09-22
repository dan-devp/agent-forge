package com.devp.forge.agent;

import com.devp.forge.config.DataProperties;
import com.devp.forge.store.FileStore;

import org.springframework.stereotype.Repository;

/** {@code files/agents/<id>/v<n>.md} */
@Repository
public class AgentRepository extends FileStore<Agent> {

	public AgentRepository(DataProperties data) {
		super(data.filesDir(), new AgentCodec(), data.isSeed());
	}
}
