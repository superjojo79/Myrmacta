package com.flowforge.infrastructure.portal;

import com.flowforge.domain.workflow.portal.port.in.ExpirePortalLinksUseCase;
import com.flowforge.domain.workflow.portal.port.out.PortalIdentityRepository;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** TODO §68 D-PORTAL-EXPIRY — statut {@code EXPIRED} + notification ; l'accès vérifie déjà l'expiration. */
@Component
@ConditionalOnProperty(prefix = "app.portal", name = "enabled", havingValue = "true")
class PortalLinkExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(PortalLinkExpiryJob.class);

    private final ExpirePortalLinksUseCase useCase;
    private final PortalIdentityRepository identities;

    PortalLinkExpiryJob(ExpirePortalLinksUseCase useCase, PortalIdentityRepository identities) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
        this.identities = Objects.requireNonNull(identities, "identities");
    }

    @Scheduled(fixedDelayString = "${app.portal.expiry.poll-delay:15m}")
    void expireDue() {
        int expired = useCase.expireDue();
        if (expired > 0) {
            log.info("Portal link expiry job expired {} link(s)", expired);
        }
        // TODO §104 (T9) — tentatives échues, identités non soumises échues ou d'un lien qui n'est plus actif (NRN chiffré
        // compris) : supprimées. Les traces des soumissions restent.
        int purged = identities.purgeExpired(java.time.Instant.now());
        if (purged > 0) {
            log.info("Portal identity cleanup removed {} row(s)", purged);
        }
    }
}
