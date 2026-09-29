package com.flowforge.domain.signature.usecase;

import com.flowforge.domain.signature.SignatureAccessResolver;
import com.flowforge.domain.signature.SignatureAccessResolver.ResolvedAccess;
import com.flowforge.domain.signature.SignatureOutcomeHandler;
import com.flowforge.domain.signature.model.SignatureOutcome;
import com.flowforge.domain.signature.model.SignatureProgression;
import com.flowforge.domain.signature.model.SignatureRequest;
import com.flowforge.domain.signature.model.SignatureStatus;
import com.flowforge.domain.signature.model.Signer;
import com.flowforge.domain.signature.model.SignerKind;
import com.flowforge.domain.signature.model.SignerStatus;
import com.flowforge.domain.signature.port.in.DecideSignatureUseCase;
import com.flowforge.domain.signature.port.out.SignatureRequestRepository;
import com.flowforge.domain.workflow.portal.model.PortalAccessDeniedException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;

/**
 * TODO §72 — un signataire signe ou refuse. La progression (modes, requis / facultatifs) est celle de
 * {@link SignatureProgression} ; la persistance est un compare-and-set atomique sur la demande et ses
 * signataires : une double soumission ou une décision concurrente perd et reçoit le refus uniforme
 * (S-DEC-3). L'actrice est tracée par {@link Signer#actorId()} (D-SIGNATURE-ACTOR).
 */
public final class DecideSignatureUseCaseImpl implements DecideSignatureUseCase {

    static final String REASON_REQUIRED_DECLINED = "REQUIRED_DECLINED";
    static final String REASON_UNREACHABLE = "NO_JOINABLE_SIGNER";

    private final SignatureRequestRepository repository;
    private final SignatureAccessResolver accessResolver;
    private final SignatureOutcomeHandler outcomeHandler;
    private final Clock clock;

    public DecideSignatureUseCaseImpl(
            SignatureRequestRepository repository,
            SignatureAccessResolver accessResolver,
            SignatureOutcomeHandler outcomeHandler,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver");
        this.outcomeHandler = Objects.requireNonNull(outcomeHandler, "outcomeHandler");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Result decideByToken(String token, Choice choice, String reason) {
        ResolvedAccess access = accessResolver.resolve(token);
        return apply(access.request(), access.signer(), choice, reason);
    }

    @Override
    public Result decideInternal(String userId, UUID signerId, Choice choice, String reason) {
        Signer signer = repository.findSignerById(signerId)
                .filter(s -> s.kind() == SignerKind.INTERNAL && userId != null && userId.equals(s.userId()))
                .orElseThrow(() -> new PortalAccessDeniedException("no internal signer " + signerId + " for user"));
        SignatureRequest request = repository.findById(signer.requestId())
                .orElseThrow(() -> new PortalAccessDeniedException("request gone for signer " + signerId));
        return apply(request, signer, choice, reason);
    }

    /** Tentatives face à une décision concurrente d'un autre signataire (le compare-and-set sur la demande sérialise). */
    private static final int MAX_ATTEMPTS = 3;

    private Result apply(SignatureRequest request, Signer signer, Choice choice, String rawReason) {
        String reason = choice == Choice.DECLINE ? cleanReason(rawReason) : null;
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
                        now, reason);
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
                return new Result(currentRequest.id(), decided, next.status());
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

    /** Motif libre borné : caractères de contrôle retirés, 500 caractères au plus (échappé à l'affichage). */
    static String cleanReason(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("\\p{Cntrl}", " ").strip();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() > Signer.MAX_DECLINE_REASON) {
            throw new IllegalArgumentException("reason must be at most " + Signer.MAX_DECLINE_REASON + " characters");
        }
        return cleaned;
    }
}
