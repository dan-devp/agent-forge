package com.devp.agent.api;

import com.devp.agent.ai.ChatService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Erster echter Modellaufruf: ein Turn, kein Verlauf. */
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

	/** {@code provider} und {@code soul} sind optional — leer bedeutet Default bzw. aktive SOUL. */
	public record ChatRequest(
			@NotBlank(message = "message darf nicht leer sein")
			@Size(max = 4000, message = "message ist zu lang (max. 4000 Zeichen)")
			String message,

			String provider,

			String soul) {
	}
}
