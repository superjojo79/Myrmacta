package com.flowforge.infrastructure.portal;

import com.flowforge.domain.workflow.portal.port.in.ExpirePortalLinksUseCase;
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

    PortalLinkExpiryJob(ExpirePortalLinksUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
    }

    @Scheduled(fixedDelayString = "${app.portal.expiry.poll-delay:15m}")
    void expireDue() {
        int expired = useCase.expireDue();
        if (expired > 0) {
            log.info("Portal link expiry job expired {} link(s)", expired);
        }
    }
}
