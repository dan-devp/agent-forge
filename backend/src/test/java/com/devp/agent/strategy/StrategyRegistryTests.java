package com.devp.agent.strategy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.devp.agent.profile.Profile;
import com.devp.agent.profile.Profile.StrategyConfig;
import com.devp.agent.strategy.StrategyRegistry.Active;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyRegistryTests {

	private static Strategy named(String id) {
		return () -> id;
	}

	@Test
	void registersById() {
		StrategyRegistry registry = new StrategyRegistry(List.of(named("a"), named("b")));

		assertThat(registry.ids()).containsExactly("a", "b");
		assertThat(registry.knows("a")).isTrue();
		assertThat(registry.knows("c")).isFalse();
	}

	/** Two beans with the same id would make a profile ambiguous — fail at startup. */
	@Test
	void rejectsDuplicateIds() {
		assertThatThrownBy(() -> new StrategyRegistry(List.of(named("a"), named("a"))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("'a'");
	}

	/** Activation keeps profile order and pairs each strategy with its own parameters. */
	@Test
	void activatesInProfileOrderWithConfig() {
		StrategyRegistry registry = new StrategyRegistry(List.of(named("a"), named("b")));
		Profile profile = new Profile("p", "P", 1, null, List.of(
				new StrategyConfig("b", Map.of("x", 1)),
				new StrategyConfig("a", Map.of())), Map.of(), "p.md");

		List<Active> active = registry.activate(profile);

		assertThat(active).extracting(a -> a.strategy().id()).containsExactly("b", "a");
		assertThat(active.getFirst().config().intParam("x", 0)).isEqualTo(1);
	}

	/** All hooks are optional — a strategy implementing none of them is valid and inert. */
	@Test
	void hooksDefaultToNoOp() {
		Strategy inert = named("inert");
		RunContext context = new RunContext(UUID.randomUUID(), "goal", null, null, null);
		StrategyConfig config = new StrategyConfig("inert", Map.of());

		inert.beforeRun(context, config);
		inert.afterToolRound(context, config, List.of());
		inert.afterRun(context, config, null, List.of());
		inert.betweenRuns(config);

		assertThat(inert.beforeFinish(context, config, "anything")).isEqualTo(Optional.empty());
		assertThat(context.promptAdditions()).isEmpty();
		assertThat(context.extraTools()).isEmpty();
	}

	@Test
	void requireTextRejectsUntilPresent() {
		RequireTextStrategy strategy = new RequireTextStrategy();
		RunContext context = new RunContext(UUID.randomUUID(), "goal", null, null, null);
		StrategyConfig config = new StrategyConfig("require-text", Map.of("text", "Rest"));

		assertThat(strategy.beforeFinish(context, config, "14 rest 2")).isEmpty();
		assertThat(strategy.beforeFinish(context, config, "14,29")).hasValueSatisfying(f -> assertThat(f).contains("'Rest'"));
		assertThat(strategy.beforeFinish(context, config, null)).isPresent();
		assertThat(strategy.beforeFinish(context, new StrategyConfig("require-text", Map.of()), "x")).isEmpty();
	}
}
