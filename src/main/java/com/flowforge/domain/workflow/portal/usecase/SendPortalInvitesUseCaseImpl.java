package com.flowforge.domain.workflow.portal.usecase;

import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.workflow.portal.PortalParticipantNotifier;
import com.flowforge.domain.workflow.portal.model.PortalLink;
import com.flowforge.domain.workflow.portal.model.PortalLinkStatus;
import com.flowforge.domain.workflow.portal.model.PortalLinkStatusReason;
import com.flowforge.domain.workflow.portal.port.in.SendPortalInvitesUseCase;
import com.flowforge.domain.workflow.portal.port.out.PortalInviteSenderPort;
import com.flowforge.domain.workflow.portal.port.out.PortalLinkRepository;
import com.flowforge.domain.workflow.portal.port.out.PortalTaskLookupPort;
import com.flowforge.domain.workflow.portal.port.out.PortalTokenPort;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * TODO §68 D-PORTAL-INVITE-DELIVERY — le jeton n'existe qu'ici, en mémoire, le temps de l'envoi :
 * jamais en base en clair (seul son hash), jamais dans l'outbox de notification.
 *
 * <p>Ordre : envoi <em>puis</em> activation. Activer d'abord laisserait, en cas d'échec d'envoi, un
 * lien {@code ACTIVE} dont personne n'a le jeton. Contrepartie acceptée : si l'activation perd une
 * course (lien révoqué entre-temps), l'e-mail parti porte un jeton qui ne sera jamais valide. Chaque
 * tentative génère un nouveau jeton (T-SEND-2).
 */
public final class SendPortalInvitesUseCaseImpl implements SendPortalInvitesUseCase {

    public static final String REVOKED_BY_SENDER = "portal-invite-sender";

    private final PortalLinkRepository repository;
    private final PortalTokenPort tokenPort;
    private final PortalInviteSenderPort senderPort;
    private final PortalTaskLookupPort taskLookup;
    private final DossierRepository dossierRepository;
    private final PortalParticipantNotifier notifier;
    private final Clock clock;
    private final int maxAttempts;
    private final int batchSize;

    /** Durée pendant laquelle un lien pris pour l'envoi n'est plus proposé à un autre exécutant. */
    static final java.time.Duration SEND_LEASE = java.time.Duration.ofMinutes(5);

    public SendPortalInvitesUseCaseImpl(
            PortalLinkRepository repository,
            PortalTokenPort tokenPort,
            PortalInviteSenderPort senderPort,
            PortalTaskLookupPort taskLookup,
            DossierRepository dossierRepository,
            PortalParticipantNotifier notifier,
            Clock clock,
            int maxAttempts,
            int batchSize) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tokenPort = Objects.requireNonNull(tokenPort, "tokenPort");
        this.senderPort = Objects.requireNonNull(senderPort, "senderPort");
        this.taskLookup = Objects.requireNonNull(taskLookup, "taskLookup");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
    }

    @Override
    public int sendPending() {
        int activated = 0;
        for (PortalLink link : repository.findByStatus(PortalLinkStatus.PENDING_SEND, batchSize)) {
            if (sendOne(link)) {
                activated++;
            }
        }
        return activated;
    }

    private boolean sendOne(PortalLink link) {
        // D5 : l'e-mail part AVANT la transition (choix de conception : un envoi qui échoue n'active rien) ; sans prise
        // préalable, deux exécutants enverraient chacun une invitation avec un jeton différent (un lien mort pour le tiers).
        if (!repository.claimForSend(link, clock.instant(), SEND_LEASE)) {
            return false;
        }
        Optional<PortalTaskLookupPort.OpenTask> task = taskLookup.findOpenTask(link.taskId());
        if (task.isEmpty()) {
            repository.transition(link,
                    link.revoked(clock.instant(), REVOKED_BY_SENDER, PortalLinkStatusReason.TASK_GONE));
            return false;
        }
        String dossierReference = dossierRepository.findById(link.dossierId())
                .map(d -> d.reference())
                .orElse("");
        String token = tokenPort.newToken();
        java.time.Instant sentAt = clock.instant();
        PortalLink activated = link.activated(tokenPort.hash(token), sentAt);
        try {
            senderPort.send(link, token, new PortalInviteSenderPort.InviteContext(
                    task.get().name(), dossierReference, activated.expiresAt()));
        } catch (RuntimeException ex) {
            PortalLink failed = link.sendFailed(maxAttempts);
            if (repository.transition(link, failed) && failed.status() == PortalLinkStatus.NOT_SENT) {
                notifier.notifyNotSent(failed);
            }
            return false;
        }
        return repository.transition(link, activated);
    }
}
