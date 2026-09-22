package com.devp.forge.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where everything that is not code lives.
 *
 * <p>{@code app.data-dir} is one directory on the host, mounted into the container. Under it
 * {@code files/<kind>/<id>/v<n>.md} holds the authored, reusable entities (models, agents,
 * tasks) and {@code db/} the Postgres cluster. One directory to back up, one to wipe.
 */
@ConfigurationProperties(prefix = "app")
public class DataProperties {

	private Path dataDir = Path.of("./data");

	/**
	 * Copy the bundled example files into an empty {@code files/<kind>} directory on start.
	 * Off in tests that want a clean store.
	 */
	private boolean seed = true;

	public Path getDataDir() {
		return dataDir;
	}

	public void setDataDir(Path dataDir) {
		this.dataDir = dataDir;
	}

	public Path filesDir() {
		return dataDir.resolve("files");
	}

	public boolean isSeed() {
		return seed;
	}

	public void setSeed(boolean seed) {
		this.seed = seed;
	}
}
