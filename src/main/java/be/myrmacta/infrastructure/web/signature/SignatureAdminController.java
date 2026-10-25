package be.myrmacta.infrastructure.web.signature;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.port.in.ManageSignatureUseCase;
import be.myrmacta.infrastructure.web.dossier.AccessContextFactory;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO §72 US-4 — demandes de signature d'un dossier, vues par l'instructeur (chaîne BFF authentifiée,
 * CSRF). Lecture avec VIEW (contact masqué sans EDIT), annulation et renvoi avec EDIT sur le type (A2).
 */
@RestController
@RequestMapping("/api")
@ConditionalOnExpression("'${app.signature.provider:none}' != 'none'")
class SignatureAdminController {

    private final ManageSignatureUseCase useCase;
    private final AccessContextFactory accessContextFactory;

    SignatureAdminController(ManageSignatureUseCase useCase, AccessContextFactory accessContextFactory) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @GetMapping("/dossiers/{dossierId}/signature-requests")
    @Transactional(readOnly = true)
    SignatureRequestListResponse listSignatureRequests(@PathVariable UUID dossierId, Authentication authentication) {
        ManageSignatureUseCase.Listing listing = useCase.list(dossierId, accessContextFactory.user(authentication));
        return new SignatureRequestListResponse(
                listing.canManage(), listing.requests().stream().map(SignatureRequestResponse::from).toList());
    }

    @PostMapping("/signature-requests/{requestId}/cancel")
    @Transactional
    SignatureRequestResponse cancel(
            @PathVariable UUID requestId,
            @RequestParam(name = "dryRun", defaultValue = "false") boolean dryRun,
            Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        return SignatureRequestResponse.from(useCase.cancel(requestId, actor, dryRun));
    }

    @PostMapping("/signature-requests/{requestId}/signers/{signerId}/resend")
    @Transactional
    SignatureRequestResponse resend(@PathVariable UUID requestId, @PathVariable UUID signerId, Authentication authentication) {
        return SignatureRequestResponse.from(useCase.resend(requestId, signerId, accessContextFactory.user(authentication)));
    }

    record SignatureRequestListResponse(boolean canManage, List<SignatureRequestResponse> requests) {}

    record SignatureRequestResponse(
            UUID id,
            String status,
            String statusReason,
            String mode,
            boolean simulated,
            String providerCode,
            UUID sourceElementId,
            Instant deadlineAt,
            Instant createdAt,
            Instant completedAt,
            boolean canManage,
            List<SignatureSignerResponse> signers) {
        static SignatureRequestResponse from(ManageSignatureUseCase.View view) {
            SignatureRequest r = view.request();
            return new SignatureRequestResponse(r.id(), r.status().name(), r.statusReason(), r.mode().name(), r.simulated(),
                    r.providerCode(), r.sourceElementId(), r.deadlineAt(), r.createdAt(), r.completedAt(),
                    view.canManage(),
                    view.signers().stream().map(s -> SignatureSignerResponse.from(s, view.canManage())).toList());
        }
    }

    /** {@code contact} : adresse masquée ou identifiant utilisateur, jamais l'adresse complète ; absent sans EDIT. */
    record SignatureSignerResponse(
            UUID id,
            int position,
            String kind,
            boolean required,
            String displayName,
            String contact,
            String status,
            Instant decidedAt,
            String declineReason,
            boolean invited) {
        static SignatureSignerResponse from(Signer s, boolean canManage) {
            String contact = !canManage ? null : (s.email() != null ? s.maskedEmail() : s.userId());
            return new SignatureSignerResponse(s.id(), s.position(), s.kind().name(), s.required(), s.displayName(), contact,
                    s.status().name(), s.decidedAt(), s.declineReason(), s.inviteSentAt() != null);
        }
    }
}
