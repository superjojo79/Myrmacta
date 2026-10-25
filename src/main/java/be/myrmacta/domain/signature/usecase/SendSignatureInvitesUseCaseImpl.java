package be.myrmacta.domain.signature.usecase;

import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.signature.SignatureNotifier;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.port.in.SendSignatureInvitesUseCase;
import be.myrmacta.domain.signature.port.out.SignatureInviteSenderPort;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import be.myrmacta.domain.workflow.portal.port.out.PortalTokenPort;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * TODO §72 — invitations des signataires externes. En parallèle tous les externes sont {@code PENDING}
 * dès l'émission ; en séquentiel, seul le signataire dont c'est le tour l'est (D-SIGNATURE-PROGRESSION-SYNC).
 * Le jeton est généré ici, seul son hash est stocké ; un échec d'envoi laisse le signataire invitable
 * (tentative + 1) jusqu'au plafond, où les participants sont prévenus.
 */
public final class SendSignatureInvitesUseCaseImpl implements SendSignatureInvitesUseCase {

    private final SignatureRequestRepository repository;
    private final PortalTokenPort tokenPort;
    private final SignatureInviteSenderPort sender;
    private final DossierRepository dossierRepository;
    private final SignatureNotifier notifier;
    private final Clock clock;
    private final int maxAttempts;
    private final int batchSize;

    public SendSignatureInvitesUseCaseImpl(
            SignatureRequestRepository repository,
            PortalTokenPort tokenPort,
            SignatureInviteSenderPort sender,
            DossierRepository dossierRepository,
            SignatureNotifier notifier,
            Clock clock,
            int maxAttempts,
            int batchSize) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tokenPort = Objects.requireNonNull(tokenPort, "tokenPort");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
    }

    @Override
    public int sendPending() {
        int sent = 0;
        for (Signer signer : repository.findInvitable(maxAttempts, batchSize)) {
            if (sendOne(signer)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean sendOne(Signer signer) {
        Optional<SignatureRequest> request = repository.findById(signer.requestId());
        if (request.isEmpty() || request.get().status() != SignatureStatus.PENDING) {
            return false;
        }
        String reference = dossierRepository.findById(request.get().dossierId())
                .map(d -> d.reference())
                .orElse("");
        String token = tokenPort.newToken();
        // Réserver d'abord (hash + envoi marqué, compare-and-set) : si un renvoi ou une autre instance est passé
        // avant, on n'envoie rien — jamais de lien mort dans la boîte du signataire.
        Signer claimed = signer.withToken(tokenPort.hash(token)).invited(clock.instant());
        if (!repository.updateSigner(claimed)) {
            return false;
        }
        try {
            sender.send(claimed, token, new SignatureInviteSenderPort.InviteContext(
                    request.get().sourceStorageRef(), reference, request.get().deadlineAt(),
                    request.get().simulated()));
        } catch (RuntimeException ex) {
            int attempts = signer.inviteAttempts() + 1;
            Signer current = repository.findSignerById(signer.id()).orElse(claimed);
            if (repository.updateSigner(current.invitationFailed(attempts)) && attempts >= maxAttempts) {
                notifier.notifyInviteFailed(request.get(), signer.maskedEmail());
            }
            return false;
        }
        return true;
    }
}
