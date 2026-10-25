package be.myrmacta.infrastructure.signature;

import be.myrmacta.domain.signature.port.in.DispatchSignatureOutcomesUseCase;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** TODO §72 — reprend les processus dont la demande de signature est terminée (D-SIGNATURE-DISPATCH). */
public class SignatureOutcomeDispatcherJob {

    private static final Logger log = LoggerFactory.getLogger(SignatureOutcomeDispatcherJob.class);

    private final DispatchSignatureOutcomesUseCase useCase;

    public SignatureOutcomeDispatcherJob(DispatchSignatureOutcomesUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
    }

    @Scheduled(fixedDelayString = "${app.signature.dispatch.poll-delay:15s}")
    void dispatchPending() {
        int resumed = useCase.dispatchPending();
        if (resumed > 0) {
            log.info("Signature dispatcher resumed {} process(es)", resumed);
        }
    }
}
