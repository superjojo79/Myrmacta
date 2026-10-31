package be.myrmacta.infrastructure.web.user;

import be.myrmacta.domain.user.model.UnknownUserException;
import be.myrmacta.domain.user.model.UserGroupConflictException;
import be.myrmacta.domain.user.model.UserGroupNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "be.myrmacta.infrastructure.web.user")
public class UserGroupExceptionHandler {

    @ExceptionHandler(UserGroupNotFoundException.class)
    ProblemDetail handleNotFound(UserGroupNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "User group not found", ex.getMessage());
    }

    @ExceptionHandler({UserGroupConflictException.class, DataIntegrityViolationException.class})
    ProblemDetail handleConflict(Exception ex) {
        String detail = ex instanceof DataIntegrityViolationException ? "A conflicting user group already exists" : ex.getMessage();
        return problem(HttpStatus.CONFLICT, "User group conflict", detail);
    }

    @ExceptionHandler({UnknownUserException.class, IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ProblemDetail handleBadRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
