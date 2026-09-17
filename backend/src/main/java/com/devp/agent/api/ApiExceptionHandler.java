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

/** Einheitliche Fehlerausgabe als RFC-7807 ProblemDetail. */
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(UnknownProviderException.class)
	public ProblemDetail handleUnknownProvider(UnknownProviderException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
		problem.setTitle("Unbekannter Provider");
		return problem;
	}

	@ExceptionHandler(UnknownSoulException.class)
	public ProblemDetail handleUnknownSoul(UnknownSoulException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Unbekannte SOUL");
		return problem;
	}

	/** Kaputte SOUL-Datei ist ein Konfigurationsfehler des Betreibers, kein Client-Fehler. */
	@ExceptionHandler(SoulParseException.class)
	public ProblemDetail handleSoulParse(SoulParseException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
		problem.setTitle("SOUL fehlerhaft");
		return problem;
	}

	@ExceptionHandler(UnknownTaskException.class)
	public ProblemDetail handleUnknownTask(UnknownTaskException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Unbekannte Aufgabe");
		return problem;
	}

	/** Kaputte Aufgabendatei ist ein Konfigurationsfehler des Betreibers, kein Client-Fehler. */
	@ExceptionHandler(TaskParseException.class)
	public ProblemDetail handleTaskParse(TaskParseException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
		problem.setTitle("Aufgabe fehlerhaft");
		return problem;
	}

	/** Fehlende oder widerspruechliche Angaben am Aufruf. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
		problem.setTitle("Ungueltige Anfrage");
		return problem;
	}

	@ExceptionHandler(UnknownRunException.class)
	public ProblemDetail handleUnknownRun(UnknownRunException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Unbekannter Lauf");
		return problem;
	}

	@ExceptionHandler(AiNotConfiguredException.class)
	public ProblemDetail handleNotConfigured(AiNotConfiguredException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
		problem.setTitle("AI-Provider nicht konfiguriert");
		return problem;
	}

	@ExceptionHandler(ModelCallFailedException.class)
	public ProblemDetail handleModelCall(ModelCallFailedException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
		problem.setTitle("Modellaufruf fehlgeschlagen");
		return problem;
	}
}
