package com.devp.forge.store;

/** Failure modes of the file store, one class each so the API can map them to a status. */
public final class StoreExceptions {

	private StoreExceptions() {
	}

	/** No such id, or no such version of it. */
	public static class UnknownEntityException extends RuntimeException {
		public UnknownEntityException(String kind, String id) {
			super("No %s with id '%s'.".formatted(singular(kind), id));
		}

		public UnknownEntityException(String kind, String id, int version) {
			super("No %s '%s' in version %d.".formatted(singular(kind), id, version));
		}
	}

	/** Create on an existing id, or a write that races another. */
	public static class EntityConflictException extends RuntimeException {
		public EntityConflictException(String message) {
			super(message);
		}
	}

	/** The entity does not pass its own validation — the message names the field. */
	public static class EntityInvalidException extends RuntimeException {
		public EntityInvalidException(String message) {
			super(message);
		}
	}

	/** The entity is referenced by another one and cannot be removed. */
	public static class EntityInUseException extends RuntimeException {
		public EntityInUseException(String message) {
			super(message);
		}
	}

	private static String singular(String kind) {
		return kind.endsWith("s") ? kind.substring(0, kind.length() - 1) : kind;
	}
}
