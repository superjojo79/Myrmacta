package be.myrmacta.domain.signature.usecase;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.signature.SignatureOutcomeHandler;
import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignatureProgression;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerKind;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.domain.signature.port.in.ManageSignatureUseCase;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** TODO §72 (US-4, A2) — lecture VIEW (adresses masquées sans EDIT), annulation / renvoi EDIT. */
public final class ManageSignatureUseCaseImpl implements ManageSignatureUseCase {

    static final String REASON_CANCELLED_BY_USER = "CANCELLED_BY_USER";

    private final String tenantId;
    private final SignatureRequestRepository repository;
    private final DossierRepository dossierRepository;
    private final DossierAccessPolicy accessPolicy;
    private final SignatureOutcomeHandler outcomeHandler;
    private final Clock clock;
    private final java.time.Duration resendMinInterval;

    public ManageSignatureUseCaseImpl(
            String tenantId,
            SignatureRequestRepository repository,
            DossierRepository dossierRepository,
            DossierAccessPolicy accessPolicy,
            SignatureOutcomeHandler outcomeHandler,
            Clock clock,
            java.time.Duration resendMinInterval) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.outcomeHandler = Objects.requireNonNull(outcomeHandler, "outcomeHandler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.resendMinInterval = Objects.requireNonNull(resendMinInterval, "resendMinInterval");
    }

    @Override
    public Listing list(UUID dossierId, AccessContext.User actor) {
        Dossier dossier = requireDossier(dossierId);
        accessPolicy.assertCanView(dossier, actor);
        boolean canManage = accessPolicy.canEdit(dossier, actor);
        return new Listing(canManage, repository.findByDossierId(dossierId).stream()
                .map(r -> new View(r, repository.findSigners(r.id()), canManage))
                .toList());
    }

    @Override
    public View cancel(UUID requestId, AccessContext.User actor, boolean dryRun) {
        SignatureRequest request = requireRequest(requestId);
        accessPolicy.assertCanEdit(requireDossier(request.dossierId()), actor);
        if (request.status() != SignatureStatus.PENDING) {
            throw new IllegalStateException("Signature request is not pending: " + request.status());
        }
        List<Signer> signers = repository.findSigners(requestId);
        if (dryRun) {
            return new View(request, signers, true);
        }
        List<Signer> revoked = SignatureProgression.cancel(signers, clock.instant()).signers();
        if (!outcomeHandler.close(request, revoked, SignatureOutcome.CANCELLED, REASON_CANCELLED_BY_USER)) {
            throw new IllegalStateException("Signature request changed concurrently: " + requestId);
        }
        return new View(repository.findById(requestId).orElseThrow(), repository.findSigners(requestId), true);
    }

    @Override
    public View resend(UUID requestId, UUID signerId, AccessContext.User actor) {
        SignatureRequest request = requireRequest(requestId);
        accessPolicy.assertCanEdit(requireDossier(request.dossierId()), actor);
        if (request.status() != SignatureStatus.PENDING) {
            throw new IllegalStateException("Signature request is not pending: " + request.status());
        }
        Signer signer = repository.findSignerById(signerId)
                .filter(s -> s.requestId().equals(requestId))
                .orElseThrow(() -> new DossierNotFoundException("Signer not found: " + signerId));
        if (signer.status() != SignerStatus.PENDING) {
            throw new IllegalStateException("Signer is not awaiting a signature: " + signer.status());
        }
        if (signer.kind() == SignerKind.EXTERNAL) {
            // Anti-inondation d'une adresse externe : un renvoi en attente d'envoi est déjà pris en compte, et
            // deux renvois ne sont pas espacés de moins de resendMinInterval.
            if (signer.inviteSentAt() == null && signer.tokenHash() == null) {
                return new View(request, repository.findSigners(requestId), true);
            }
            if (signer.inviteSentAt() != null
                    && signer.inviteSentAt().plus(resendMinInterval).isAfter(clock.instant())) {
                throw new IllegalStateException("Invitation sent too recently; retry in a moment");
            }
            // l'ancien jeton cesse de fonctionner tout de suite ; le job d'invitations en émet un nouveau
            if (!repository.updateSigner(signer.invitationReset())) {
                throw new IllegalStateException("Signer changed concurrently: " + signerId);
            }
        } else {
            outcomeHandler.notifyNewlyPending(request, List.of(signer));
        }
        return new View(request, repository.findSigners(requestId), true);
    }

    private SignatureRequest requireRequest(UUID requestId) {
        return repository.findById(requestId)
                .filter(r -> tenantId.equals(r.tenantId()))
                .orElseThrow(() -> new DossierNotFoundException("Signature request not found: " + requestId));
    }

    private Dossier requireDossier(UUID dossierId) {
        return dossierRepository.findById(dossierId)
                .filter(d -> tenantId.equals(d.tenantId()))
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + dossierId));
    }
}
