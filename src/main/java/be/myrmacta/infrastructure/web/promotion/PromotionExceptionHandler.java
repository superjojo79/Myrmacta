package be.myrmacta.infrastructure.web.promotion;

import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.promotion.model.InvalidBundleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** TODO §83 (D4) — bundle invalide = 400 (rien n'est écrit) ; droits = 403. */
@RestControllerAdvice(basePackages = "be.myrmacta.infrastructure.web.promotion")
class PromotionExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Access denied", ex.getMessage());
    }

    @ExceptionHandler({InvalidBundleException.class, IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ProblemDetail handleBadRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, "Bundle too large", "the bundle exceeds the upload limit");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
