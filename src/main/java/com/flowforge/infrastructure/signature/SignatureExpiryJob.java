package com.flowforge.infrastructure.signature;

import com.flowforge.domain.signature.port.in.ExpireSignatureRequestsUseCase;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** TODO §72 — expire les demandes de signature dont l'échéance est dépassée. */
public class SignatureExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(SignatureExpiryJob.class);

    private final ExpireSignatureRequestsUseCase useCase;

    public SignatureExpiryJob(ExpireSignatureRequestsUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
    }

    @Scheduled(fixedDelayString = "${app.signature.expiry.poll-delay:15m}")
    void expireDue() {
        int expired = useCase.expireDue();
        if (expired > 0) {
            log.info("Signature expiry job expired {} request(s)", expired);
        }
    }
}
