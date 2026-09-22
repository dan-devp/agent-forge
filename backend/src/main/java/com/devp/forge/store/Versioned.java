package com.devp.forge.store;

/**
 * An authored entity: identified by a slug, counted in versions.
 *
 * <p>A version is a file. Saving never changes a file that exists; it writes the next one.
 * A run records id and version of everything it used, so the recipe behind any result
 * stays readable, whatever was saved since.
 */
public interface Versioned {

	String id();

	int version();
}
