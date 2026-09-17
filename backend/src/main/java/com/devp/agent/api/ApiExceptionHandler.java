package com.devp.agent.api;

import com.devp.agent.ai.ChatClientRegistry.UnknownProviderException;
import com.devp.agent.ai.ChatService.AiNotConfiguredException;
import com.devp.agent.ai.ChatService.ModelCallFailedException;
import com.devp.agent.api.RunController.UnknownRunException;
import com.devp.agent.soul.SoulParser.SoulParseException;
import com.devp.agent.soul.SoulRepository.UnknownSoulException;
import com.devp.agent.task.TaskParser.TaskParseException;
import com.devp.agent.task.TaskRepository.UnknownTaskException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Uniform error output as RFC 7807 ProblemDetail. */
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(UnknownProviderException.class)
	public ProblemDetail handleUnknownProvider(UnknownProviderException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Unknown provider", ex);
	}

	@ExceptionHandler(UnknownSoulException.class)
	public ProblemDetail handleUnknownSoul(UnknownSoulException ex) {
		return problem(HttpStatus.NOT_FOUND, "Unknown SOUL", ex);
	}

	/** A broken SOUL file is an operator's configuration error, not a client error. */
	@ExceptionHandler(SoulParseException.class)
	public ProblemDetail handleSoulParse(SoulParseException ex) {
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "SOUL invalid", ex);
	}

	@ExceptionHandler(UnknownTaskException.class)
	public ProblemDetail handleUnknownTask(UnknownTaskException ex) {
		return problem(HttpStatus.NOT_FOUND, "Unknown task", ex);
	}

	/** A broken task file is an operator's configuration error, not a client error. */
	@ExceptionHandler(TaskParseException.class)
	public ProblemDetail handleTaskParse(TaskParseException ex) {
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Task invalid", ex);
	}

	/** Missing or contradictory values on the request. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex);
	}

	@ExceptionHandler(UnknownRunException.class)
	public ProblemDetail handleUnknownRun(UnknownRunException ex) {
		return problem(HttpStatus.NOT_FOUND, "Unknown run", ex);
	}

	@ExceptionHandler(AiNotConfiguredException.class)
	public ProblemDetail handleNotConfigured(AiNotConfiguredException ex) {
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "AI provider not configured", ex);
	}

	@ExceptionHandler(ModelCallFailedException.class)
	public ProblemDetail handleModelCall(ModelCallFailedException ex) {
		return problem(HttpStatus.BAD_GATEWAY, "Model call failed", ex);
	}

	private static ProblemDetail problem(HttpStatus status, String title, RuntimeException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
		problem.setTitle(title);
		return problem;
	}
}
