package be.myrmacta.infrastructure.web.signature;

import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.signature.model.HostedSignatureException;
import be.myrmacta.domain.signature.model.SignedUploadRejectedException;
import be.myrmacta.domain.signature.model.SignedUploadRequiredException;
import be.myrmacta.domain.signature.model.SignedUploadTooLargeException;
import be.myrmacta.infrastructure.security.SecurityAuditLog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** TODO §72 — erreurs de l'API instructeur des demandes de signature. */
@RestControllerAdvice(assignableTypes = {SignatureAdminController.class, MySignaturesController.class})
class SignatureAdminExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, Authentication authentication) {
        SecurityAuditLog.accessDenied(request, authentication, ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "Access denied", ex.getMessage());
    }

    /** Signataire interne sans accès (autre utilisateur, pas son tour, demande close, dossier illisible). */
    @ExceptionHandler(be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException.class)
    ProblemDetail handleNoAccess(be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException ex) {
        return problem(HttpStatus.NOT_FOUND, "Signature resource not found", "Ressource introuvable.");
    }

    @ExceptionHandler(DossierNotFoundException.class)
    ProblemDetail handleNotFound(DossierNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Signature resource not found", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalid(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid signature request", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleConflict(IllegalStateException ex) {
        return problem(HttpStatus.CONFLICT, "Signature conflict", ex.getMessage());
    }

    /** La cérémonie est chez le fournisseur (D-SIGNATURE-LINK). */
    @ExceptionHandler(HostedSignatureException.class)
    ProblemDetail handleHostedSignature(HostedSignatureException ex) {
        return problem(HttpStatus.CONFLICT, "Signature chez le fournisseur",
                "Cette signature se fait sur le site du fournisseur.");
    }

    /** Signature apportée : le clic « Signer » est refusé, il faut téléverser le PDF signé (TODO §138). */
    @ExceptionHandler(SignedUploadRequiredException.class)
    ProblemDetail handleSignedUploadRequired(SignedUploadRequiredException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Téléversement requis",
                "Cette signature se fait en téléversant le PDF signé.");
        problem.setProperty("code", "SIGNED_UPLOAD_REQUIRED");
        return problem;
    }

    /** PDF signé refusé : le motif ne décrit que le fichier du signataire, il lui est restitué tel quel (TODO §138). */
    @ExceptionHandler(SignedUploadRejectedException.class)
    ProblemDetail handleSignedUploadRejected(SignedUploadRejectedException ex) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Document signé refusé",
                "Le document signé a été refusé.");
        problem.setProperty("code", ex.rejection().name());
        return problem;
    }

    @ExceptionHandler({SignedUploadTooLargeException.class, MaxUploadSizeExceededException.class})
    ProblemDetail handleTooLarge(Exception ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop volumineux", "Le fichier dépasse la taille autorisée.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
