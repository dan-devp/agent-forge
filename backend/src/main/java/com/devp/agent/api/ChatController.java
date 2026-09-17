package com.devp.agent.api;

import com.devp.agent.ai.ChatService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A single model call: one turn, no history. */
@RestController
@RequestMapping("/api")
@Validated
public class ChatController {

	private final ChatService chatService;

	public ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	@PostMapping("/chat")
	public ChatService.ChatResult chat(@Validated @RequestBody ChatRequest request) {
		return chatService.ask(request.message(), request.provider(), request.soul());
	}

	/** {@code provider} and {@code soul} are optional — empty means default resp. active SOUL. */
	public record ChatRequest(
			@NotBlank(message = "message must not be blank")
			@Size(max = 4000, message = "message is too long (max. 4000 characters)")
			String message,

			String provider,

			String soul) {
	}
}
