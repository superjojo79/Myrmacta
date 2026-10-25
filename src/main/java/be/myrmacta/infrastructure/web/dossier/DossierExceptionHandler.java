package be.myrmacta.infrastructure.web.dossier;

import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.DossierConflictException;
import be.myrmacta.domain.dossier.model.DossierFieldValidationException;
import be.myrmacta.domain.dossier.model.DossierNotEligibleForPurgeException;
import be.myrmacta.domain.dossier.model.DossierElementNotFoundException;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierTypeSchemaIncompatibleException;
import be.myrmacta.domain.dossier.model.DuplicateDossierRelationException;
import be.myrmacta.domain.dossier.model.IllegalDossierStatusTransitionException;
import be.myrmacta.domain.dossier.model.InactiveDossierTypeException;
import be.myrmacta.domain.dossier.model.InvalidDossierElementException;
import be.myrmacta.domain.dossier.model.InvalidRetentionPolicyException;
import be.myrmacta.domain.dossier.model.TenantRetentionSettingsNotFoundException;
import be.myrmacta.infrastructure.security.SecurityAuditLog;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(basePackages = "be.myrmacta.infrastructure.web.dossier")
public class DossierExceptionHandler {

    @ExceptionHandler(DossierFieldValidationException.class)
    ProblemDetail handleFieldValidation(DossierFieldValidationException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Dossier field validation failed", ex.getMessage());
        problem.setProperty("errors", ex.errors());
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, Authentication authentication) {
        SecurityAuditLog.accessDenied(request, authentication, ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "Access denied", ex.getMessage());
    }

    @ExceptionHandler({DossierNotFoundException.class, DossierElementNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Dossier resource not found", ex.getMessage());
    }

    @ExceptionHandler(TenantRetentionSettingsNotFoundException.class)
    ProblemDetail handleRetentionSettingsNotFound(TenantRetentionSettingsNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Retention settings not found", ex.getMessage());
    }

    @ExceptionHandler(InvalidDossierElementException.class)
    ProblemDetail handleInvalidElement(InvalidDossierElementException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid dossier element", ex.getMessage());
    }

    @ExceptionHandler(InvalidRetentionPolicyException.class)
    ProblemDetail handleInvalidRetentionPolicy(InvalidRetentionPolicyException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid retention policy", ex.getMessage());
    }

    @ExceptionHandler(DossierTypeSchemaIncompatibleException.class)
    ProblemDetail handleSchemaIncompatible(DossierTypeSchemaIncompatibleException ex) {
        String examples = ex.sampleReferences().isEmpty()
                ? ""
                : " (ex. " + String.join(", ", ex.sampleReferences()) + ")";
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Dossier type schema incompatible",
                "Cette modification rend " + ex.incompatibleCount()
                        + " dossier(s) existant(s) incompatible(s) avec le nouveau schéma" + examples
                        + duplicateSentence(ex));
        problem.setProperty("incompatibleCount", ex.incompatibleCount());
        problem.setProperty("sampleReferences", ex.sampleReferences());
        // TODO §102 (U10) : valeurs en double d'un champ unique, pour que l'administrateur les résolve.
        // La liste est bornée (20) ; duplicateCount est le total, referenceCount celui des détenteurs d'une valeur.
        problem.setProperty("duplicates", ex.duplicates());
        problem.setProperty("duplicateCount", ex.duplicateCount());
        return problem;
    }

    /** Le détail nomme le total. Quand la liste est tronquée à 20, il précise combien de valeurs sont citées. */
    private static String duplicateSentence(DossierTypeSchemaIncompatibleException ex) {
        if (ex.duplicates().isEmpty()) {
            return "";
        }
        String sentence = " ; " + ex.duplicateCount()
                + " valeur(s) d'un champ unique sont partagées par des dossiers en cours";
        if (ex.duplicates().size() < ex.duplicateCount()) {
            sentence += " (" + ex.duplicates().size() + " citées)";
        }
        return sentence;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Upload too large", "The uploaded file exceeds the maximum allowed size");
    }

    @ExceptionHandler({
            IllegalDossierStatusTransitionException.class,
            DossierConflictException.class,
            DuplicateDossierRelationException.class,
            InactiveDossierTypeException.class,
            DossierNotEligibleForPurgeException.class,
            ObjectOptimisticLockingFailureException.class,
            OptimisticLockException.class
    })
    ProblemDetail handleConflict(Exception ex) {
        return problem(HttpStatus.CONFLICT, "Dossier conflict", ex.getMessage());
    }

    // Unique/FK integrity violations map to 409. Dossier hard-delete (purge) is allowed; remaining
    // integrity errors are still treated as conflicts.
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        return problem(HttpStatus.CONFLICT, "Dossier conflict", "Unique constraint or data integrity violation");
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class
    })
    ProblemDetail handleBadRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
