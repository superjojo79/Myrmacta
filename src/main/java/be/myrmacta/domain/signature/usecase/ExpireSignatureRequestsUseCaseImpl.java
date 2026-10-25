package be.myrmacta.domain.signature.usecase;

import be.myrmacta.domain.signature.SignatureOutcomeHandler;
import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignatureProgression;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.port.in.ExpireSignatureRequestsUseCase;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * TODO §72 D-SIGNATURE-EXPIRY — l'échéance est un fait de la demande : encore ouverte, elle passe
 * {@code EXPIRED} avec tous ses signataires non décidés. Idempotent et tolérant au retard : la
 * requête ne retient que les demandes {@code PENDING} échues ; un compare-and-set perdu (décision
 * concurrente) laisse la main à l'autre appel.
 */
public final class ExpireSignatureRequestsUseCaseImpl implements ExpireSignatureRequestsUseCase {

    static final String REASON_DEADLINE = "DEADLINE";

    private final SignatureRequestRepository repository;
    private final SignatureOutcomeHandler outcomeHandler;
    private final Clock clock;
    private final int batchSize;

    public ExpireSignatureRequestsUseCaseImpl(
            SignatureRequestRepository repository, SignatureOutcomeHandler outcomeHandler, Clock clock, int batchSize) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.outcomeHandler = Objects.requireNonNull(outcomeHandler, "outcomeHandler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.batchSize = batchSize;
    }

    @Override
    public int expireDue() {
        Instant now = clock.instant();
        int expired = 0;
        for (SignatureRequest request : repository.findPendingWithDeadlineBefore(now, batchSize)) {
            var signers = SignatureProgression.expire(repository.findSigners(request.id()), now).signers();
            if (outcomeHandler.close(request, signers, SignatureOutcome.EXPIRED, REASON_DEADLINE)) {
                expired++;
            }
        }
        return expired;
    }
}
