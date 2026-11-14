package be.myrmacta.domain.signature;

import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignatureProgression;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.domain.signature.port.in.DecideSignatureUseCase;
import be.myrmacta.domain.signature.port.in.DecideSignatureUseCase.Choice;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * TODO §72 — applique la décision d'un signataire à sa demande : progression ({@link SignatureProgression},
 * modes parallèle et séquentiel, requis et facultatifs), puis compare-and-set atomique sur la demande et
 * ses signataires, puis suites ({@link SignatureOutcomeHandler}). Une double soumission ou une décision
 * concurrente perd et reçoit le refus uniforme (S-DEC-3).
 *
 * <p>Partagé par le clic « Signer » / « Refuser » ({@code DecideSignatureUseCaseImpl}) et par le
 * téléversement d'un PDF signé, qui décide {@code SIGN} dans sa propre transaction (TODO §138). Sans
 * contrôle d'accès : l'appelant l'a fait.
 */
public final class SignatureDecisionApplier {

    public static final String REASON_REQUIRED_DECLINED = "REQUIRED_DECLINED";
    public static final String REASON_UNREACHABLE = "NO_JOINABLE_SIGNER";

    /** Tentatives face à une décision concurrente d'un autre signataire (le compare-and-set sur la demande sérialise). */
    private static final int MAX_ATTEMPTS = 3;

    private final SignatureRequestRepository repository;
    private final SignatureOutcomeHandler outcomeHandler;
    private final Clock clock;

    public SignatureDecisionApplier(
            SignatureRequestRepository repository, SignatureOutcomeHandler outcomeHandler, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.outcomeHandler = Objects.requireNonNull(outcomeHandler, "outcomeHandler");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * @param reason motif déjà nettoyé, utilisé seulement pour un refus
     * @throws PortalAccessDeniedException demande close ou échue, signataire plus en attente, décision
     *     concurrente qui l'emporte
     */
    public DecideSignatureUseCase.Result apply(
            SignatureRequest request, Signer signer, Choice choice, String reason) {
        SignatureRequest currentRequest = request;
        for (int attempt = 1; ; attempt++) {
            Instant now = clock.instant();
            if (currentRequest.status() != SignatureStatus.PENDING || !currentRequest.deadlineAt().isAfter(now)) {
                throw new PortalAccessDeniedException("request " + currentRequest.id() + " cannot be decided ("
                        + currentRequest.status() + ")");
            }
            // relecture des signataires : la progression s'appuie sur l'état courant, la version fait foi
            List<Signer> current = repository.findSigners(currentRequest.id());
            Signer self = current.stream().filter(s -> s.id().equals(signer.id())).findFirst().orElse(null);
            if (self == null || self.status() != SignerStatus.PENDING) {
                throw new PortalAccessDeniedException("signer " + signer.id() + " cannot decide");
            }
            SignatureProgression.Result progression;
            try {
                progression = SignatureProgression.decide(
                        currentRequest.mode(), current, signer.id(),
                        choice == Choice.SIGN ? SignatureProgression.Decision.SIGN : SignatureProgression.Decision.DECLINE,
                        now, choice == Choice.DECLINE ? reason : null);
            } catch (IllegalStateException | IllegalArgumentException ex) {
                throw new PortalAccessDeniedException("signer " + signer.id() + " no longer pending");
            }
            SignatureRequest next = currentRequest;
            if (progression.closes()) {
                next = currentRequest.closed(progression.outcome(), closingReason(progression.outcome()), now);
            }
            if (repository.update(next, progression.signers())) {
                if (progression.closes()) {
                    outcomeHandler.afterClosed(next, progression.outcome());
                } else {
                    outcomeHandler.notifyNewlyPending(next, progression.newlyPending());
                }
                SignerStatus decided = progression.signers().stream()
                        .filter(s -> s.id().equals(signer.id()))
                        .findFirst()
                        .orElseThrow()
                        .status();
                return new DecideSignatureUseCase.Result(currentRequest.id(), decided, next.status());
            }
            // Perdu contre une autre décision : relire la demande (version courante) et réessayer.
            Optional<SignatureRequest> reread = repository.findById(currentRequest.id());
            if (attempt >= MAX_ATTEMPTS || reread.isEmpty()) {
                throw new PortalAccessDeniedException("concurrent decision on request " + currentRequest.id());
            }
            currentRequest = reread.get();
        }
    }

    private static String closingReason(SignatureOutcome outcome) {
        return switch (outcome) {
            case DECLINED -> REASON_REQUIRED_DECLINED;
            case FAILED -> REASON_UNREACHABLE;
            default -> null;
        };
    }
}
