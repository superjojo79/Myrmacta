package be.myrmacta.infrastructure.persistence.precheck;

import be.myrmacta.domain.precheck.model.PrecheckFinding;
import be.myrmacta.domain.precheck.model.PrecheckRun;
import be.myrmacta.domain.precheck.model.PrecheckSpec;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

/** TODO §106 (D-PRECHECK-STORE) — une exécution de pré-vérification (changeset 040). */
@Entity
@Table(name = "precheck_run")
class PrecheckRunJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    @Column(name = "trigger_kind", nullable = false, length = 16)
    private String triggerKind;

    @Column(name = "requested_by", nullable = false, length = 128)
    private String requestedBy;

    @Column(name = "workflow_link_id")
    private UUID workflowLinkId;

    @Column(name = "spec_json", nullable = false, columnDefinition = "text")
    private String specJson;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(length = 300)
    private String failure;

    @Column(length = 32)
    private String provider;

    @Column(length = 128)
    private String model;

    @Column(name = "prompt_version", length = 16)
    private String promptVersion;

    @Column(name = "input_sha256", length = 64)
    private String inputSha256;

    @Column(length = 1000)
    private String summary;

    @Column(name = "tokens_in", nullable = false)
    private long tokensIn;

    @Column(name = "tokens_out", nullable = false)
    private long tokensOut;

    @Column(nullable = false)
    private int discarded;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected PrecheckRunJpaEntity() {
    }

    static PrecheckRunJpaEntity fromDomain(PrecheckRun run, String tenantId, ObjectMapper mapper) {
        PrecheckRunJpaEntity e = new PrecheckRunJpaEntity();
        e.id = run.id();
        e.tenantId = tenantId;
        e.dossierId = run.dossierId();
        e.triggerKind = run.trigger().name();
        e.requestedBy = run.requestedBy();
        e.workflowLinkId = run.workflowLinkId();
        e.specJson = mapper.writeValueAsString(run.spec());
        e.status = run.status().name();
        e.attempts = run.attempts();
        e.failure = run.failure();
        e.provider = run.provider();
        e.model = run.model();
        e.promptVersion = run.promptVersion();
        e.inputSha256 = run.inputSha256();
        e.summary = run.summary();
        e.tokensIn = run.tokensIn();
        e.tokensOut = run.tokensOut();
        e.discarded = run.discarded();
        e.createdAt = run.createdAt();
        e.finishedAt = run.finishedAt();
        return e;
    }

    UUID id() {
        return id;
    }

    PrecheckRun toDomain(ObjectMapper mapper, List<PrecheckFinding> findings) {
        return new PrecheckRun(id, dossierId, PrecheckRun.Trigger.valueOf(triggerKind), requestedBy, workflowLinkId,
                mapper.readValue(specJson, PrecheckSpec.class), PrecheckRun.Status.valueOf(status), attempts, failure,
                provider, model, promptVersion, inputSha256, summary, tokensIn, tokensOut, discarded, createdAt,
                finishedAt, findings);
    }
}
