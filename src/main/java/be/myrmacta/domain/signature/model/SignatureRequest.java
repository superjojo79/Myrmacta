package be.myrmacta.domain.signature.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Demande de signature d'un {@code PRODUCED_DOC} (TODO §72). Immuable, versionnée.
 *
 * <p>{@code sourceDigest} fige le contenu émis (SHA-256) : la demande reste liée au document tel
 * qu'il était à l'émission. {@code simulated} est vrai pour le stub. {@code outcomeDispatchedAt}
 * est non nul une fois le process repris (D-SIGNATURE-DISPATCH).
 */
public record SignatureRequest(
        UUID id,
        String tenantId,
        UUID dossierId,
        String processInstanceId,
        String activityId,
        UUID sourceElementId,
        String sourceStorageRef,
        String sourceDigest,
        String providerCode,
        String providerReference,
        SignatureMode mode,
        SignatureStatus status,
        String statusReason,
        boolean simulated,
        String assuranceLevel,
        Instant deadlineAt,
        Instant outcomeDispatchedAt,
        int dispatchAttempts,
        Instant createdAt,
        Instant completedAt,
        String createdBy,
        long version) {

    public SignatureRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dossierId, "dossierId");
        Objects.requireNonNull(processInstanceId, "processInstanceId");
        Objects.requireNonNull(activityId, "activityId");
        Objects.requireNonNull(providerCode, "providerCode");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(deadlineAt, "deadlineAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(createdBy, "createdBy");
    }

    public boolean isDispatchPending() {
        return status.isTerminal() && outcomeDispatchedAt == null;
    }

    /** Clôture la demande avec l'issue donnée (statut terminal, horodatage, motif). */
    public SignatureRequest closed(SignatureOutcome outcome, String reason, Instant at) {
        Objects.requireNonNull(outcome, "outcome");
        return new SignatureRequest(id, tenantId, dossierId, processInstanceId, activityId, sourceElementId,
                sourceStorageRef, sourceDigest, providerCode, providerReference, mode, outcome.status(), reason,
                simulated, assuranceLevel, deadlineAt, outcomeDispatchedAt, dispatchAttempts, createdAt, at,
                createdBy, version);
    }

    public SignatureRequest withProviderReference(String reference) {
        return new SignatureRequest(id, tenantId, dossierId, processInstanceId, activityId, sourceElementId,
                sourceStorageRef, sourceDigest, providerCode, reference, mode, status, statusReason, simulated,
                assuranceLevel, deadlineAt, outcomeDispatchedAt, dispatchAttempts, createdAt, completedAt,
                createdBy, version);
    }

    public SignatureRequest withSimulation(boolean simulatedFlag, String level) {
        return new SignatureRequest(id, tenantId, dossierId, processInstanceId, activityId, sourceElementId,
                sourceStorageRef, sourceDigest, providerCode, providerReference, mode, status, statusReason,
                simulatedFlag, level, deadlineAt, outcomeDispatchedAt, dispatchAttempts, createdAt, completedAt,
                createdBy, version);
    }

    public SignatureRequest dispatched(Instant at) {
        return new SignatureRequest(id, tenantId, dossierId, processInstanceId, activityId, sourceElementId,
                sourceStorageRef, sourceDigest, providerCode, providerReference, mode, status, statusReason,
                simulated, assuranceLevel, deadlineAt, at, dispatchAttempts, createdAt, completedAt, createdBy,
                version);
    }

    public SignatureRequest withDispatchAttempts(int attempts) {
        return new SignatureRequest(id, tenantId, dossierId, processInstanceId, activityId, sourceElementId,
                sourceStorageRef, sourceDigest, providerCode, providerReference, mode, status, statusReason,
                simulated, assuranceLevel, deadlineAt, outcomeDispatchedAt, attempts, createdAt, completedAt,
                createdBy, version);
    }

    public SignatureRequest withVersion(long newVersion) {
        return new SignatureRequest(id, tenantId, dossierId, processInstanceId, activityId, sourceElementId,
                sourceStorageRef, sourceDigest, providerCode, providerReference, mode, status, statusReason,
                simulated, assuranceLevel, deadlineAt, outcomeDispatchedAt, dispatchAttempts, createdAt,
                completedAt, createdBy, newVersion);
    }
}
