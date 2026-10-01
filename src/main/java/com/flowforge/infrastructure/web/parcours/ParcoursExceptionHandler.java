package com.flowforge.infrastructure.web.parcours;

import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.parcours.model.InvalidParcoursException;
import com.flowforge.domain.parcours.model.ParcoursConflictException;
import com.flowforge.domain.parcours.model.ParcoursNotFoundException;
import com.flowforge.domain.workflow.model.DraftConflictException;
import com.flowforge.domain.workflow.model.InvalidBpmnException;
import com.flowforge.domain.workflow.model.UnknownBlockReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** TODO §88 (LC5) — parcours incohérent ou BPMN refusé = 400 (le message nomme l'étape) ; conflit = 409 ; absent = 404. */
@RestControllerAdvice(basePackages = "com.flowforge.infrastructure.web.parcours")
class ParcoursExceptionHandler {

    @ExceptionHandler({InvalidParcoursException.class, InvalidBpmnException.class, IllegalArgumentException.class})
    ProblemDetail invalid(RuntimeException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid parcours", ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException ex) {
        // un record du modèle (étape, branche) refuse sa propre valeur : son message nomme l'étape, on le rend
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidParcoursException invalid) {
                return problem(HttpStatus.BAD_REQUEST, "Invalid parcours", invalid.getMessage());
            }
        }
        return problem(HttpStatus.BAD_REQUEST, "Bad request", "the request body is not valid");
    }

    @ExceptionHandler({ParcoursConflictException.class, DraftConflictException.class})
    ProblemDetail conflict(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler({ParcoursNotFoundException.class, UnknownBlockReferenceException.class})
    ProblemDetail notFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Parcours not found", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail denied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Access denied", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
