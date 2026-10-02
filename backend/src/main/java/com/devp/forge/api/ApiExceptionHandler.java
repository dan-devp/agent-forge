package com.devp.forge.api;

import com.devp.forge.ai.ChatClientRegistry.AiNotConfiguredException;
import com.devp.forge.ai.ChatClientRegistry.ModelCallFailedException;
import com.devp.forge.api.RunController.UnknownRunException;
import com.devp.forge.store.StoreExceptions.EntityConflictException;
import com.devp.forge.store.StoreExceptions.EntityInUseException;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;
import com.devp.forge.store.StoreExceptions.UnknownEntityException;
import com.devp.forge.training.TrainingEngine.UnknownSessionException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Uniform error output as RFC 7807 ProblemDetail. No stack trace reaches the client. */
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(UnknownEntityException.class)
	public ProblemDetail handleUnknownEntity(UnknownEntityException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", ex);
	}

	@ExceptionHandler(UnknownRunException.class)
	public ProblemDetail handleUnknownRun(UnknownRunException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", ex);
	}

	@ExceptionHandler(UnknownSessionException.class)
	public ProblemDetail handleUnknownSession(UnknownSessionException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", ex);
	}

	@ExceptionHandler(EntityInvalidException.class)
	public ProblemDetail handleInvalid(EntityInvalidException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid", ex);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex);
	}

	@ExceptionHandler(EntityConflictException.class)
	public ProblemDetail handleConflict(EntityConflictException ex) {
		return problem(HttpStatus.CONFLICT, "Conflict", ex);
	}

	@ExceptionHandler(EntityInUseException.class)
	public ProblemDetail handleInUse(EntityInUseException ex) {
		return problem(HttpStatus.CONFLICT, "In use", ex);
	}

	@ExceptionHandler(AiNotConfiguredException.class)
	public ProblemDetail handleNotConfigured(AiNotConfiguredException ex) {
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "Model not usable", ex);
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
