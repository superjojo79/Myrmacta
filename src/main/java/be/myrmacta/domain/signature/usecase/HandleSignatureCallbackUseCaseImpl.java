package be.myrmacta.domain.signature.usecase;

import be.myrmacta.domain.signature.SignatureOutcomeHandler;
import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.domain.signature.port.in.HandleSignatureCallbackUseCase;
import be.myrmacta.domain.signature.port.out.SignatureMetricsPort;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * TODO §72 — retour d'un fournisseur réel. L'issue est celle du fournisseur (les décisions par
 * signataire ne servent qu'à l'affichage) ; les signataires non décidés sont clos avec la demande
 * ({@code EXPIRED} pour une expiration, sinon {@code REVOKED}). Une référence inconnue est une erreur
 * de requête (400 pour le fournisseur) ; un événement sur une demande déjà close est ignoré, sauf un
 * {@code SIGNED} hébergé qui remplace un refus local : le document signé chez le fournisseur fait foi.
 */
public final class HandleSignatureCallbackUseCaseImpl implements HandleSignatureCallbackUseCase {

    static final String REASON_PROVIDER_CALLBACK = "PROVIDER_CALLBACK";

    private final SignatureRequestRepository repository;
    private final SignatureOutcomeHandler outcomeHandler;
    private final SignaturePort signaturePort;
    private final SignatureMetricsPort metrics;
    private final Clock clock;

    public HandleSignatureCallbackUseCaseImpl(
            SignatureRequestRepository repository,
            SignatureOutcomeHandler outcomeHandler,
            SignaturePort signaturePort,
            SignatureMetricsPort metrics,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.outcomeHandler = Objects.requireNonNull(outcomeHandler, "outcomeHandler");
        this.signaturePort = Objects.requireNonNull(signaturePort, "signaturePort");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean apply(SignaturePort.SignatureCallbackEvent event) {
        SignatureRequest request = repository.findByProviderReference(event.providerReference())
                .orElseThrow(() -> {
                    metrics.callbackRejected("unknown_reference");
                    // le fournisseur peut rappeler avant que la référence soit enregistrée : à rejouer, pas un échec définitif
                    return new be.myrmacta.domain.connector.model.CallbackRetryLaterException(
                            "unknown signature reference");
                });
        if (request.status() != SignatureStatus.PENDING && !signedAfterLocalDecline(request, event)) {
            metrics.callbackRejected("already_closed");
            return false;
        }
        Instant now = clock.instant();
        List<Signer> signers = new ArrayList<>();
        for (Signer signer : repository.findSigners(request.id())) {
            SignerStatus reported = event.signerDecisions().get(signer.id());
            if (reported != null && signer.status().isOpen() && !reported.isOpen()) {
                signers.add(signer.decided(reported, now, null));
            } else if (signer.status().isOpen()) {
                signers.add(signer.decided(
                        event.outcome() == SignatureOutcome.EXPIRED ? SignerStatus.EXPIRED : SignerStatus.REVOKED,
                        now, null));
            } else {
                signers.add(signer);
            }
        }
        boolean closed = outcomeHandler.close(request, signers, event.outcome(), REASON_PROVIDER_CALLBACK);
        if (!closed) {
            metrics.callbackRejected("concurrent");
        }
        return closed;
    }

    /** Le refus local a pu précéder le webhook du document réellement signé (revue SIG-2). */
    private boolean signedAfterLocalDecline(SignatureRequest request, SignaturePort.SignatureCallbackEvent event) {
        return request.status() == SignatureStatus.DECLINED
                && event.outcome() == SignatureOutcome.SIGNED
                && signaturePort.capabilities().hostedSigningUrl();
    }
}
