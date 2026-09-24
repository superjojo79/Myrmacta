package com.flowforge.infrastructure.portal;

import com.flowforge.domain.workflow.portal.port.in.SendPortalInvitesUseCase;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** TODO §68 — traite les liens {@code PENDING_SEND} (jeton + e-mail + activation). */
@Component
@ConditionalOnProperty(prefix = "app.portal", name = "enabled", havingValue = "true")
class PortalInviteSenderJob {

    private static final Logger log = LoggerFactory.getLogger(PortalInviteSenderJob.class);

    private final SendPortalInvitesUseCase useCase;

    PortalInviteSenderJob(SendPortalInvitesUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
    }

    @Scheduled(fixedDelayString = "${app.portal.send.poll-delay:10s}")
    void sendPending() {
        int sent = useCase.sendPending();
        if (sent > 0) {
            log.info("Portal invite job activated {} link(s)", sent);
        }
    }
}
