package be.myrmacta.infrastructure.web.precheck;

import be.myrmacta.domain.precheck.model.PrecheckFinding;
import be.myrmacta.domain.precheck.model.PrecheckRun;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * TODO §106 (lot B, P4, H4) — exécution vue par l'agent : constats sourcés, marqués par leur origine (déterministe ou
 * modèle), et ce qu'il faut pour le marquage « Généré par IA — indicatif ». Ni la spécification ni l'empreinte des
 * entrées (réservées à l'export).
 */
record PrecheckRunResponse(
        UUID id,
        String trigger,
        String requestedBy,
        String status,
        String failure,
        String provider,
        String model,
        String promptVersion,
        String summary,
        long tokensIn,
        long tokensOut,
        int discarded,
        Instant createdAt,
        Instant finishedAt,
        List<Finding> findings) {

    record Finding(UUID id, String kind, String origin, String sourceType, String sourceRef, String message,
                   Double confidence, String decision, String decidedBy, Instant decidedAt) {
        static Finding from(PrecheckFinding f) {
            return new Finding(f.id(), f.kind().name(), f.origin().name(), f.sourceType().name(), f.sourceRef(),
                    f.message(), f.confidence(), f.decision() == null ? null : f.decision().name(), f.decidedBy(),
                    f.decidedAt());
        }
    }

    static PrecheckRunResponse from(PrecheckRun run) {
        return new PrecheckRunResponse(run.id(), run.trigger().name(), run.requestedBy(), run.status().name(),
                run.failure(), run.provider(), run.model(), run.promptVersion(), run.summary(), run.tokensIn(),
                run.tokensOut(), run.discarded(), run.createdAt(), run.finishedAt(),
                run.findings().stream().map(Finding::from).toList());
    }
}
