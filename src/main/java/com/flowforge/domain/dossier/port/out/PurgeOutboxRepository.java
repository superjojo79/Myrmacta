package com.flowforge.domain.dossier.port.out;

import com.flowforge.domain.dossier.model.PurgeOutboxRecord;
import com.flowforge.domain.dossier.model.PurgeOutboxStatus;
import java.util.List;
import java.util.UUID;

public interface PurgeOutboxRepository {

    /** Idempotent insert — a conflict on the UNIQUE {@code dossier_id} constraint is a no-op. */
    void insert(PurgeOutboxRecord record);

    boolean existsByDossierId(UUID dossierId);

    /** {@code PENDING} ∪ {@code FAILED}, oldest first. */
    List<PurgeOutboxRecord> findRetryable(int limit);

    /** Atomic claim: {@code PENDING}/{@code FAILED} → {@code PROCESSING}. */
    boolean claim(UUID id);

    void markCompleted(UUID id);

    void markFailed(UUID id, String error);

    List<PurgeOutboxRecord> list(PurgeOutboxStatus statusFilter, int limit);

    long countByStatus(PurgeOutboxStatus status);
}
