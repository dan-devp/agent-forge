package com.devp.agent.strategy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.devp.agent.profile.Profile;
import com.devp.agent.profile.Profile.StrategyConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * All strategies known to the application, by id. A profile may only name these.
 */
@Component
public class StrategyRegistry {

	private static final Logger log = LoggerFactory.getLogger(StrategyRegistry.class);

	private final Map<String, Strategy> strategies;

	public StrategyRegistry(List<Strategy> beans) {
		Map<String, Strategy> byId = new LinkedHashMap<>();
		for (Strategy strategy : beans) {
			Strategy previous = byId.put(strategy.id(), strategy);
			if (previous != null) {
				throw new IllegalStateException("Two strategies share the id '%s': %s and %s"
						.formatted(strategy.id(), previous.getClass().getName(), strategy.getClass().getName()));
			}
		}
		this.strategies = Collections.unmodifiableMap(byId);
		log.info("{} strateg{} registered: {}", byId.size(), byId.size() == 1 ? "y" : "ies", byId.keySet());
	}

	public boolean knows(String id) {
		return strategies.containsKey(id);
	}

	public Set<String> ids() {
		return strategies.keySet();
	}

	/** The active strategies of a profile, paired with their configuration, in profile order. */
	public List<Active> activate(Profile profile) {
		return profile.strategies().stream()
				.map(config -> new Active(strategies.get(config.id()), config))
				.toList();
	}

	/** One strategy as activated by one profile. */
	public record Active(Strategy strategy, StrategyConfig config) {
	}
}
