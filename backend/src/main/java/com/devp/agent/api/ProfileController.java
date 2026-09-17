package com.devp.agent.api;

import java.util.List;
import java.util.Map;

import com.devp.agent.profile.Profile;
import com.devp.agent.profile.Profile.StrategyConfig;
import com.devp.agent.profile.ProfileRepository;
import com.devp.agent.strategy.StrategyRegistry;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Strategy profiles. Read plus reload — editing happens in the files so the history stays
 * in Git, the same decision as for SOULs and tasks.
 */
@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

	private final ProfileRepository profiles;
	private final StrategyRegistry strategies;

	public ProfileController(ProfileRepository profiles, StrategyRegistry strategies) {
		this.profiles = profiles;
		this.strategies = strategies;
	}

	@GetMapping
	public ProfileListResponse list() {
		String defaultId = profiles.defaultId();
		List<ProfileSummary> list = profiles.list().stream()
				.map(profile -> ProfileSummary.of(profile, profile.id().equals(defaultId)))
				.toList();
		return new ProfileListResponse(defaultId, list, strategies.ids().stream().toList());
	}

	/** Including the further sections of the file. */
	@GetMapping("/{id}")
	public ProfileDetail detail(@PathVariable String id) {
		Profile profile = profiles.resolve(id);
		return new ProfileDetail(ProfileSummary.of(profile, profile.id().equals(profiles.defaultId())),
				profile.sections(), profile.source());
	}

	@PostMapping("/reload")
	public ProfileListResponse reload() {
		profiles.reload();
		return list();
	}

	public record ProfileSummary(String id, String name, int version, String description,
			List<StrategyConfig> strategies, boolean isDefault) {

		static ProfileSummary of(Profile profile, boolean isDefault) {
			return new ProfileSummary(profile.id(), profile.name(), profile.version(), profile.description(),
					profile.strategies(), isDefault);
		}
	}

	/** {@code availableStrategies} lists what a profile may name at all. */
	public record ProfileListResponse(String defaultProfile, List<ProfileSummary> profiles,
			List<String> availableStrategies) {
	}

	public record ProfileDetail(ProfileSummary profile, Map<String, String> sections, String source) {
	}
}
