package be.myrmacta.infrastructure.persistence.precheck;

import be.myrmacta.domain.precheck.model.FindingKind;
import be.myrmacta.domain.precheck.model.PrecheckFinding;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** TODO §106 (D-PRECHECK-STORE) — un constat d'une exécution et la décision de l'agent (changeset 040). */
@Entity
@Table(name = "precheck_finding")
class PrecheckFindingJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(nullable = false)
    private int ordinal;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(nullable = false, length = 16)
    private String origin;

    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType;

    @Column(name = "source_ref", nullable = false, length = 100)
    private String sourceRef;

    @Column(nullable = false, length = 400)
    private String message;

    private Double confidence;

    @Column(length = 16)
    private String decision;

    @Column(name = "decided_by", length = 128)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected PrecheckFindingJpaEntity() {
    }

    static PrecheckFindingJpaEntity fromDomain(UUID runId, PrecheckFinding f) {
        PrecheckFindingJpaEntity e = new PrecheckFindingJpaEntity();
        e.id = f.id();
        e.runId = runId;
        e.ordinal = f.ordinal();
        e.kind = f.kind().name();
        e.origin = f.origin().name();
        e.sourceType = f.sourceType().name();
        e.sourceRef = PrecheckFinding.truncate(f.sourceRef(), 100);
        e.message = f.message();
        e.confidence = f.confidence();
        e.decision = f.decision() == null ? null : f.decision().name();
        e.decidedBy = f.decidedBy();
        e.decidedAt = f.decidedAt();
        return e;
    }

    UUID runId() {
        return runId;
    }

    PrecheckFinding toDomain() {
        return new PrecheckFinding(id, ordinal, FindingKind.valueOf(kind), PrecheckFinding.Origin.valueOf(origin),
                PrecheckFinding.SourceType.valueOf(sourceType), sourceRef, message, confidence,
                decision == null ? null : PrecheckFinding.Decision.valueOf(decision), decidedBy, decidedAt);
    }
}
