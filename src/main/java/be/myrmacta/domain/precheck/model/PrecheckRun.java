package be.myrmacta.domain.precheck.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * TODO §106 (lot B, P9, H4, HC9) — une exécution de la pré-vérification et son journal : modèle et version, version du
 * prompt, empreinte SHA-256 des entrées (jamais les entrées en clair), synthèse, jetons, constats et décisions.
 * {@code failure} est un motif générique (jamais le message du fournisseur).
 */
public record PrecheckRun(
        UUID id,
        UUID dossierId,
        Trigger trigger,
        String requestedBy,
        UUID workflowLinkId,
        PrecheckSpec spec,
        Status status,
        int attempts,
        String failure,
        String provider,
        String model,
        String promptVersion,
        String inputSha256,
        String summary,
        long tokensIn,
        long tokensOut,
        int discarded,
        Instant createdAt,
        Instant finishedAt,
        List<PrecheckFinding> findings) {

    public static final int MAX_SUMMARY_CHARS = 800;

    public enum Trigger { WORKFLOW, MANUAL, MCP }

    public enum Status { PENDING, RUNNING, SUCCEEDED, FAILED }

    public PrecheckRun {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dossierId, "dossierId");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(requestedBy, "requestedBy");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        findings = findings == null ? List.of() : List.copyOf(findings);
        summary = summary == null ? null : PrecheckFinding.truncate(summary.strip(), MAX_SUMMARY_CHARS);
    }

    public static PrecheckRun pending(UUID dossierId, Trigger trigger, String requestedBy, UUID workflowLinkId,
            PrecheckSpec spec, Instant now) {
        return new PrecheckRun(UUID.randomUUID(), dossierId, trigger, requestedBy, workflowLinkId, spec, Status.PENDING, 0,
                null, null, null, null, null, null, 0, 0, 0, now, null, List.of());
    }

    public PrecheckRun completed(PrecheckOutcome outcome, Instant now) {
        return new PrecheckRun(id, dossierId, trigger, requestedBy, workflowLinkId, spec, outcome.status(), attempts,
                outcome.failure(), outcome.provider(), outcome.model(), outcome.promptVersion(), outcome.inputSha256(),
                outcome.summary(), outcome.tokensIn(), outcome.tokensOut(), outcome.discarded(), createdAt, now,
                outcome.findings());
    }

    public boolean finished() {
        return status == Status.SUCCEEDED || status == Status.FAILED;
    }
}
