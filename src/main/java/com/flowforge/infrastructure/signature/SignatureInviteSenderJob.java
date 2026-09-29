package com.flowforge.infrastructure.signature;

import com.flowforge.domain.signature.port.in.SendSignatureInvitesUseCase;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** TODO §72 — envoie les invitations des signataires externes {@code PENDING}. */
public class SignatureInviteSenderJob {

    private static final Logger log = LoggerFactory.getLogger(SignatureInviteSenderJob.class);

    private final SendSignatureInvitesUseCase useCase;

    public SignatureInviteSenderJob(SendSignatureInvitesUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
    }

    @Scheduled(fixedDelayString = "${app.signature.send.poll-delay:10s}")
    void sendPending() {
        int sent = useCase.sendPending();
        if (sent > 0) {
            log.info("Signature invite job sent {} invitation(s)", sent);
        }
    }
}
