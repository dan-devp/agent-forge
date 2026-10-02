package com.devp.forge.training;

import com.devp.forge.agent.Agent;
import com.devp.forge.ai.ChatClientRegistry;
import com.devp.forge.model.Model;
import com.devp.forge.model.ModelKind;
import com.devp.forge.model.ModelRepository;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * The coach agent's model answers the brief, with the coach's system prompt. No tools: the
 * coach reasons over evidence it was given, it does not go looking.
 */
@Component
public class ChatCoach implements Coach {

	private final ChatClientRegistry clients;
	private final ModelRepository models;

	public ChatCoach(ChatClientRegistry clients, ModelRepository models) {
		this.clients = clients;
		this.models = models;
	}

	@Override
	public Proposal propose(Agent coach, CoachBrief brief) {
		Model model = models.get(coach.modelId());
		if (model.kind() != ModelKind.CHAT) {
			throw new EntityInvalidException("coach '%s' points at decision model '%s', which writes no text"
					.formatted(coach.id(), model.id()));
		}
		long start = System.currentTimeMillis();
		ChatResponse response = clients.client(model).prompt()
				.system(coach.systemPrompt())
				.user(brief.render())
				.call()
				.chatResponse();
		long duration = System.currentTimeMillis() - start;

		String text = response == null || response.getResult() == null ? null
				: response.getResult().getOutput().getText();
		Integer tokens = response == null || response.getMetadata().getUsage() == null ? null
				: response.getMetadata().getUsage().getTotalTokens();
		String reported = response == null ? null : response.getMetadata().getModel();
		return Proposal.parse(text, reported == null || reported.isBlank() ? model.modelId() : reported, tokens,
				duration);
	}
}
