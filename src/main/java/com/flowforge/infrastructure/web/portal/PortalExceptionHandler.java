package com.flowforge.infrastructure.web.portal;

import com.flowforge.domain.dossier.model.DossierConflictException;
import com.flowforge.domain.dossier.model.InvalidDossierElementException;
import com.flowforge.domain.workflow.portal.model.PortalAccessDeniedException;
import com.flowforge.infrastructure.security.ClientIpResolver;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * TODO §68 — réponses du portail public. Tout refus d'accès produit <strong>exactement</strong> le
 * même corps (US-3, T-ACCESS-2) : le motif réel ne va qu'au log serveur (IP + motif, jamais le
 * jeton) et au compteur {@value #DENIED_METRIC}.
 */
@RestControllerAdvice(assignableTypes = {
        PortalController.class, SignaturePortalController.class, PortalIdentityController.class})
class PortalExceptionHandler {

    static final String DENIED_METRIC = "flowforge.portal.access.denied";
    private static final Logger log = LoggerFactory.getLogger(PortalExceptionHandler.class);

    private final MeterRegistry meterRegistry;
    private final ClientIpResolver clientIpResolver;

    PortalExceptionHandler(MeterRegistry meterRegistry, ClientIpResolver clientIpResolver) {
        this.meterRegistry = meterRegistry;
        this.clientIpResolver = clientIpResolver;
    }

    @ExceptionHandler(PortalAccessDeniedException.class)
    ProblemDetail handleDenied(PortalAccessDeniedException ex, HttpServletRequest request) {
        meterRegistry.counter(DENIED_METRIC).increment();
        log.info("Portal access denied from {}: {}", client(request), ex.internalReason());
        return deniedProblem();
    }

    @ExceptionHandler(PortalRateLimitedException.class)
    ProblemDetail handleRateLimited(PortalRateLimitedException ex, HttpServletRequest request) {
        meterRegistry.counter(DENIED_METRIC, "reason", "rate-limited").increment();
        log.info("Portal rate limit reached for {}", client(request));
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Trop de requêtes",
                "Trop de tentatives. Réessayez dans une minute.");
    }

    @ExceptionHandler({IllegalArgumentException.class, InvalidDossierElementException.class,
            HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class})
    ProblemDetail handleInvalid(Exception ex) {
        String detail = ex instanceof HttpMessageNotReadableException ? "Requête illisible." : ex.getMessage();
        return problem(HttpStatus.BAD_REQUEST, "Saisie invalide", detail);
    }

    @ExceptionHandler(DossierConflictException.class)
    ProblemDetail handleConflict(DossierConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Dossier clos", "Ce dossier n'accepte plus de pièces.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop volumineux", "Le fichier dépasse la taille autorisée.");
    }

    /** C3 (revue adversariale) — corps multipart mal formé : erreur du client, pas du serveur. */
    @ExceptionHandler(MultipartException.class)
    ProblemDetail handleMalformedMultipart(MultipartException ex, HttpServletRequest request) {
        log.info("Portal malformed multipart from {}: {}", client(request), ex.getClass().getSimpleName());
        return problem(HttpStatus.BAD_REQUEST, "Saisie invalide", "Requête illisible.");
    }

    /**
     * Phase 7 — sans ce filet, une erreur imprévue (panne de stockage, base indisponible) partait vers
     * {@code /error}, hors de la chaîne portail : réponse de la chaîne BFF (connexion Keycloak) ou
     * détail technique exposé au tiers. Le détail reste dans le log serveur.
     *
     * <p>C3 (revue adversariale) — le filet attrapait aussi les exceptions standard de Spring MVC
     * (mauvais {@code Content-Type} → 415, etc.) : 500 et pile d'appels au niveau ERROR, déclenchables
     * par un anonyme sans jeton. Une {@link ErrorResponse} 4xx garde son statut, journalisée en INFO.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse standard && standard.getStatusCode().is4xxClientError()) {
            log.info("Portal request rejected from {} ({} {}): {}", client(request), request.getMethod(),
                    sanitize(request.getRequestURI()), ex.getClass().getSimpleName());
            return problem(HttpStatus.valueOf(standard.getStatusCode().value()), "Requête invalide",
                    "La requête n'a pas pu être traitée.");
        }
        log.error("Portal request failed ({} {})", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur inattendue",
                "Votre demande n'a pas pu être traitée. Réessayez plus tard.");
    }

    static ProblemDetail deniedProblem() {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "Lien invalide ou expiré",
                "Ce lien n'est pas ou plus valide. Contactez la personne qui vous l'a envoyé.");
        problem.setInstance(URI.create("/api/portal/task"));
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }

    private String client(HttpServletRequest request) {
        return sanitize(clientIpResolver.resolve(request));
    }

    private static String sanitize(String value) {
        return value == null ? "unknown" : value.replaceAll("[\\r\\n]", "_");
    }
}
