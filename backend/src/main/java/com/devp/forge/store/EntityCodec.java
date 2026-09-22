package com.devp.forge.store;

import java.util.Map;

import com.devp.forge.doc.MarkdownDocument;

/**
 * Translates between one entity type and its Markdown file.
 *
 * <p>The store knows directories and versions, the codec knows fields. Adding an entity
 * means one record, one codec, one directory name — nothing in the store changes.
 */
public interface EntityCodec<T extends Versioned> {

	/** Directory name under {@code files/}, plural and lowercase: {@code models}, {@code agents}. */
	String kind();

	/** Reads and validates. Throws {@link EntityInvalidException} for anything that must not be saved. */
	T decode(MarkdownDocument document);

	/** Front matter in the order the file should show it. {@code id} and {@code version} included. */
	Map<String, Object> frontMatter(T entity);

	/** Body below the front matter; {@code null} or blank for entities without prose. */
	String body(T entity);

	/** The same entity under the version the store assigned. */
	T withVersion(T entity, int version);
}
