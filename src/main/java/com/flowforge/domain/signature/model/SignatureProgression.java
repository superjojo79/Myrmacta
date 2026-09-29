package com.flowforge.domain.signature.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Règles de progression d'une demande (fonction pure, TODO §72 design § « SignatureProgression ») :
 *
 * <ol>
 *   <li>signature de <i>i</i> : <i>i</i> devient {@code SIGNED} ;
 *   <li>refus d'un <b>requis</b> : demande {@code DECLINED}, tous les non décidés {@code REVOKED} ;
 *       refus d'un <b>facultatif</b> : marqué {@code DECLINED}, la demande continue ;
 *   <li>tous les requis {@code SIGNED} : demande {@code SIGNED}, les non décidés {@code REVOKED} ;
 *   <li>{@code SEQUENTIAL} : après une décision qui ne ferme pas la demande, le premier
 *       {@code WAITING} de rang supérieur devient {@code PENDING} ; {@code PARALLEL} : tous
 *       {@code PENDING} dès l'émission ;
 *   <li>échéance : la demande encore ouverte passe {@code EXPIRED}, tous les non décidés
 *       {@code EXPIRED} (l'expiration est un fait de la demande, D-SIGNATURE-EXPIRY).
 * </ol>
 */
public final class SignatureProgression {

    public enum Decision {
        SIGN,
        DECLINE
    }

    /**
     * @param signers état complet après l'événement, trié par rang
     * @param outcome issue de la demande si elle se ferme, sinon {@code null}
     * @param newlyPending signataires qui viennent de passer {@code PENDING} (à notifier)
     */
    public record Result(List<Signer> signers, SignatureOutcome outcome, List<Signer> newlyPending) {
        public Result {
            signers = List.copyOf(signers);
            newlyPending = List.copyOf(newlyPending);
        }

        public boolean closes() {
            return outcome != null;
        }
    }

    private SignatureProgression() {}

    /** État initial à l'émission. */
    public static Result start(SignatureMode mode, List<Signer> signers) {
        List<Signer> sorted = sorted(signers);
        requireRequired(sorted);
        List<Signer> out = new ArrayList<>();
        List<Signer> pending = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            boolean active = mode == SignatureMode.PARALLEL || i == 0;
            Signer next = sorted.get(i).withStatus(active ? SignerStatus.PENDING : SignerStatus.WAITING);
            out.add(next);
            if (active) {
                pending.add(next);
            }
        }
        return new Result(out, null, pending);
    }

    public static Result decide(
            SignatureMode mode, List<Signer> signers, UUID signerId, Decision decision, Instant at, String reason) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(decision, "decision");
        List<Signer> sorted = sorted(signers);
        Signer target = sorted.stream()
                .filter(s -> s.id().equals(signerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown signer " + signerId));
        if (target.status() != SignerStatus.PENDING) {
            throw new IllegalStateException("signer " + signerId + " is not PENDING (" + target.status() + ")");
        }
        List<Signer> updated = new ArrayList<>();
        for (Signer s : sorted) {
            updated.add(s.id().equals(signerId)
                    ? s.decided(decision == Decision.SIGN ? SignerStatus.SIGNED : SignerStatus.DECLINED, at,
                            decision == Decision.DECLINE ? reason : null)
                    : s);
        }
        if (decision == Decision.DECLINE && target.required()) {
            return close(updated, SignatureOutcome.DECLINED);
        }
        boolean allRequiredSigned = updated.stream()
                .filter(Signer::required)
                .allMatch(s -> s.status() == SignerStatus.SIGNED);
        if (allRequiredSigned) {
            return close(updated, SignatureOutcome.SIGNED);
        }
        List<Signer> newly = new ArrayList<>();
        if (mode == SignatureMode.SEQUENTIAL) {
            boolean anyPending = updated.stream().anyMatch(s -> s.status() == SignerStatus.PENDING);
            if (!anyPending) {
                for (int i = 0; i < updated.size(); i++) {
                    if (updated.get(i).status() == SignerStatus.WAITING) {
                        Signer activated = updated.get(i).withStatus(SignerStatus.PENDING);
                        updated.set(i, activated);
                        newly.add(activated);
                        break;
                    }
                }
            }
        }
        boolean anyOpen = updated.stream().anyMatch(s -> s.status().isOpen());
        if (!anyOpen) {
            // Aucun requis manquant n'est encore joignable : la demande ne peut plus aboutir.
            return close(updated, SignatureOutcome.FAILED);
        }
        return new Result(updated, null, newly);
    }

    /** Échéance dépassée : la demande encore ouverte expire, tous les non décidés avec elle. */
    public static Result expire(List<Signer> signers, Instant at) {
        List<Signer> updated = new ArrayList<>();
        for (Signer s : sorted(signers)) {
            updated.add(s.status().isOpen() ? s.decided(SignerStatus.EXPIRED, at, null) : s);
        }
        return new Result(updated, SignatureOutcome.EXPIRED, List.of());
    }

    /** Annulation (instructeur, agent, process ou dossier disparu) : les non décidés sont révoqués. */
    public static Result cancel(List<Signer> signers, Instant at) {
        List<Signer> updated = new ArrayList<>();
        for (Signer s : sorted(signers)) {
            updated.add(s.status().isOpen() ? s.decided(SignerStatus.REVOKED, at, null) : s);
        }
        return new Result(updated, SignatureOutcome.CANCELLED, List.of());
    }

    private static Result close(List<Signer> signers, SignatureOutcome outcome) {
        List<Signer> out = new ArrayList<>();
        for (Signer s : signers) {
            out.add(s.status().isOpen() ? s.withStatus(SignerStatus.REVOKED) : s);
        }
        return new Result(out, outcome, List.of());
    }

    private static List<Signer> sorted(List<Signer> signers) {
        return signers.stream().sorted(Comparator.comparingInt(Signer::position)).toList();
    }

    private static void requireRequired(List<Signer> signers) {
        if (signers.isEmpty() || signers.stream().noneMatch(Signer::required)) {
            throw new IllegalArgumentException("a request needs at least one required signer");
        }
    }
}
