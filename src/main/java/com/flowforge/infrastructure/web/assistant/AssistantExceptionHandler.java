package com.flowforge.infrastructure.web.assistant;

import com.flowforge.domain.assistant.model.AssistantExceptions.ActionNotFoundException;
import com.flowforge.domain.assistant.model.AssistantExceptions.ActionNotPendingException;
import com.flowforge.domain.assistant.model.AssistantExceptions.AssistantBusyException;
import com.flowforge.domain.assistant.model.AssistantExceptions.AssistantDisabledException;
import com.flowforge.domain.assistant.model.AssistantExceptions.AssistantLimitExceededException;
import com.flowforge.domain.assistant.model.AssistantExceptions.ConversationNotFoundException;
import com.flowforge.domain.assistant.model.AssistantExceptions.ModelUnavailableException;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** TODO §86 (LC3) — inactif = 503, plafond = 429, fournisseur en échec = 502 (sans détail du fournisseur), absent = 404. */
@RestControllerAdvice(basePackages = "com.flowforge.infrastructure.web.assistant")
class AssistantExceptionHandler {

    @ExceptionHandler(AssistantDisabledException.class)
    ProblemDetail disabled(AssistantDisabledException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Assistant disabled", ex.getMessage());
    }

    @ExceptionHandler(AssistantLimitExceededException.class)
    ProblemDetail limit(AssistantLimitExceededException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Assistant limit reached", ex.getMessage());
    }

    @ExceptionHandler(ModelUnavailableException.class)
    ProblemDetail model(ModelUnavailableException ex) {
        return problem(HttpStatus.BAD_GATEWAY, "Language model unavailable", ex.getMessage());
    }

    @ExceptionHandler({AssistantBusyException.class, ActionNotPendingException.class})
    ProblemDetail conflict(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler({ConversationNotFoundException.class, ActionNotFoundException.class})
    ProblemDetail notFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Conversation not found", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail denied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Access denied", ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ProblemDetail badRequest(Exception ex) {
        // Le détail d'une lecture JSON refusée peut citer des internes : message neutre (revue LC3, B5).
        String detail = ex instanceof HttpMessageNotReadableException ? "the request body is not valid" : ex.getMessage();
        return problem(HttpStatus.BAD_REQUEST, "Bad request", detail);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
