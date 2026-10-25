package be.myrmacta.domain.signature.usecase;

import be.myrmacta.domain.signature.SignatureOutcomeHandler;
import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignatureProgression;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.port.in.CancelSignatureRequestsForProcessUseCase;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import java.time.Clock;
import java.util.Objects;

/** TODO §72 D-SIGNATURE-CANCEL-PROPAGATION — process disparu : demandes ouvertes annulées, liens invalidés. */
public final class CancelSignatureRequestsForProcessUseCaseImpl implements CancelSignatureRequestsForProcessUseCase {

    static final String REASON_PROCESS_GONE = "PROCESS_GONE";
    static final String REASON_WAIT_INTERRUPTED = "WAIT_INTERRUPTED";

    private final SignatureRequestRepository repository;
    private final SignatureOutcomeHandler outcomeHandler;
    private final Clock clock;

    public CancelSignatureRequestsForProcessUseCaseImpl(
            SignatureRequestRepository repository, SignatureOutcomeHandler outcomeHandler, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.outcomeHandler = Objects.requireNonNull(outcomeHandler, "outcomeHandler");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public int cancelForProcess(String processInstanceId) {
        int cancelled = 0;
        for (SignatureRequest request : repository.findPendingByProcessInstance(processInstanceId)) {
            var now = clock.instant();
            var signers = SignatureProgression.cancel(repository.findSigners(request.id()), now).signers();
            // le process a disparu : il n'y a rien à reprendre, la demande est marquée répartie d'emblée
            if (outcomeHandler.close(request.dispatched(now), signers, SignatureOutcome.CANCELLED, REASON_PROCESS_GONE)) {
                cancelled++;
            }
        }
        return cancelled;
    }

    @Override
    public boolean cancelInterruptedWait(java.util.UUID requestId) {
        return repository.findById(requestId)
                .filter(r -> r.status() == be.myrmacta.domain.signature.model.SignatureStatus.PENDING)
                .map(request -> {
                    var now = clock.instant();
                    var signers = SignatureProgression.cancel(repository.findSigners(request.id()), now).signers();
                    // plus personne n'attend l'issue : rien à reprendre
                    return outcomeHandler.close(
                            request.dispatched(now), signers, SignatureOutcome.CANCELLED, REASON_WAIT_INTERRUPTED);
                })
                .orElse(false);
    }
}
