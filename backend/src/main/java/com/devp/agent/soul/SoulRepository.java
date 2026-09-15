package com.devp.agent.soul;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.devp.agent.config.SoulProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Laedt alle SOUL-Definitionen aus den konfigurierten Locations und haelt sie im Speicher.
 *
 * <p>Der Bestand ist zur Laufzeit ueber {@link #reload()} austauschbar — eine geaenderte
 * SOUL soll ohne Neustart wirksam werden, sonst ist die spaetere Lernschleife nicht
 * beobachtbar.
 */
@Component
public class SoulRepository {

	private static final Logger log = LoggerFactory.getLogger(SoulRepository.class);

	/** Greift, wenn keine Definition gefunden wurde — der Agent bleibt antwortfaehig. */
	private static final Soul NOTFALL = new Soul(
			"default",
			"Agent",
			"KI-Mitarbeiter",
			0,
			"Eingebauter Notfall-Fallback, weil keine SOUL-Datei gefunden wurde.",
			List.of(),
			Map.of("Identitaet", "Du bist ein hilfreicher KI-Mitarbeiter. Antworte praezise und knapp."),
			"eingebaut");

	private final SoulProperties properties;
	private final ResourcePatternResolver resolver;

	private volatile Map<String, Soul> souls = Map.of();

	public SoulRepository(SoulProperties properties) {
		this.properties = properties;
		this.resolver = new PathMatchingResourcePatternResolver();
		reload();
	}

	/** Liest alle Locations neu ein. Gibt die Anzahl gefundener SOULs zurueck. */
	public int reload() {
		Map<String, Soul> geladen = new LinkedHashMap<>();

		for (String location : properties.getLocations()) {
			String muster = location.endsWith("/") ? location + "*.md" : location + "/*.md";
			for (Resource resource : suche(muster)) {
				Soul soul = lies(resource);
				Soul vorher = geladen.put(soul.id(), soul);
				if (vorher != null) {
					log.info("SOUL '{}' aus {} ueberschreibt {}", soul.id(), soul.quelle(), vorher.quelle());
				}
			}
		}

		// LinkedHashMap statt Map.copyOf: die Ladereihenfolge bestimmt die Anzeige im Frontend.
		this.souls = Collections.unmodifiableMap(geladen);
		if (geladen.isEmpty()) {
			log.warn("Keine SOUL gefunden in {} — es gilt der eingebaute Fallback", properties.getLocations());
		}
		else {
			log.info("{} SOUL(s) geladen: {}", geladen.size(), geladen.keySet());
		}
		return geladen.size();
	}

	private Resource[] suche(String muster) {
		try {
			return resolver.getResources(muster);
		}
		catch (IOException ex) {
			// Eine nicht existierende externe Location ist kein Fehler, nur leer.
			log.warn("SOUL-Location '{}' nicht lesbar: {}", muster, ex.getMessage());
			return new Resource[0];
		}
	}

	private Soul lies(Resource resource) {
		String quelle = resource.getFilename() == null ? resource.getDescription() : resource.getFilename();
		try (var in = resource.getInputStream()) {
			return SoulParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), quelle);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("SOUL '%s' nicht lesbar".formatted(quelle), ex);
		}
	}

	/** Aufloesung inkl. Fallback auf die aktive SOUL. */
	public Soul resolve(String angefragteId) {
		String id = (angefragteId == null || angefragteId.isBlank()) ? properties.getActive() : angefragteId;

		Soul soul = souls.get(id);
		if (soul != null) {
			return soul;
		}
		if (souls.isEmpty()) {
			return NOTFALL;
		}
		throw new UnknownSoulException("Unbekannte SOUL '%s'. Verfuegbar: %s".formatted(id, souls.keySet()));
	}

	public Collection<Soul> list() {
		return souls.isEmpty() ? List.of(NOTFALL) : List.copyOf(souls.values());
	}

	public String activeId() {
		return souls.containsKey(properties.getActive()) ? properties.getActive() : resolve(null).id();
	}

	/** Angefragte SOUL-Id existiert nicht. */
	public static class UnknownSoulException extends RuntimeException {
		public UnknownSoulException(String message) {
			super(message);
		}
	}
}
