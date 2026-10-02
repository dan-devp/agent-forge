package com.devp.forge.training;

import java.util.Locale;

import com.devp.forge.store.StoreExceptions.EntityInvalidException;

/** Who decides whether a measured candidate becomes the new best version. */
public enum Approval {

	/** The gate decides on its own; the session runs through. */
	GATE,

	/** The gate recommends, a human accepts or rejects; the session waits meanwhile. */
	MANUAL;

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Approval of(String text) {
		if (text == null || text.isBlank()) {
			return GATE;
		}
		return switch (text.strip().toLowerCase(Locale.ROOT)) {
			case "gate" -> GATE;
			case "manual" -> MANUAL;
			default -> throw new EntityInvalidException("'approval' must be gate or manual");
		};
	}
}
