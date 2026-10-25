package be.myrmacta.domain.workflow.portal.usecase;

import be.myrmacta.domain.workflow.portal.PortalParticipantNotifier;
import be.myrmacta.domain.workflow.portal.model.PortalLink;
import be.myrmacta.domain.workflow.portal.port.in.ExpirePortalLinksUseCase;
import be.myrmacta.domain.workflow.portal.port.out.PortalLinkRepository;
import java.time.Clock;
import java.util.Objects;

/**
 * TODO §68 D-PORTAL-EXPIRY — la sécurité ne dépend pas de ce job (l'accès vérifie l'expiration à
 * chaque requête) : il ne sert qu'à passer le statut et à prévenir les participants, une seule
 * fois par lien (compare-and-set, T-EXPIRY-1). La tâche reste ouverte.
 */
public final class ExpirePortalLinksUseCaseImpl implements ExpirePortalLinksUseCase {

    private final PortalLinkRepository repository;
    private final PortalParticipantNotifier notifier;
    private final Clock clock;
    private final int batchSize;

    public ExpirePortalLinksUseCaseImpl(
            PortalLinkRepository repository, PortalParticipantNotifier notifier, Clock clock, int batchSize) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.batchSize = batchSize;
    }

    @Override
    public int expireDue() {
        int expired = 0;
        for (PortalLink link : repository.findActiveExpiringBefore(clock.instant(), batchSize)) {
            PortalLink next = link.expired();
            if (repository.transition(link, next)) {
                notifier.notifyExpired(next);
                expired++;
            }
        }
        return expired;
    }
}
