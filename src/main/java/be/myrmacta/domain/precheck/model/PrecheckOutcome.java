package be.myrmacta.domain.precheck.model;

import java.util.List;
import java.util.Objects;

/**
 * TODO §106 (lot B) — résultat d'un calcul de pré-vérification, avant rangement. En échec ({@code FAILED}), les constats
 * déterministes restent et {@code failure} donne un motif générique (HC5, R7).
 */
public record PrecheckOutcome(
        PrecheckRun.Status status,
        String failure,
        String provider,
        String model,
        String promptVersion,
        String inputSha256,
        String summary,
        long tokensIn,
        long tokensOut,
        int discarded,
        List<PrecheckFinding> findings) {

    public PrecheckOutcome {
        Objects.requireNonNull(status, "status");
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    /** HC6 / C5 — comptes déterministes seulement : un routage ne dépend jamais du modèle. */
    public long deterministicCount(FindingKind kind) {
        return findings.stream()
                .filter(f -> f.origin() == PrecheckFinding.Origin.DETERMINISTIC && f.kind() == kind)
                .count();
    }
}
