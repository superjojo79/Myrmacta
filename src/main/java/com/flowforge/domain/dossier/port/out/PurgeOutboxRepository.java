package com.flowforge.domain.dossier.port.out;

import com.flowforge.domain.dossier.model.PurgeOutboxRecord;
import com.flowforge.domain.dossier.model.PurgeOutboxStatus;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
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

    PageResult<PurgeOutboxRecord> page(PurgeOutboxStatus statusFilter, PageQuery query);

    long countByStatus(PurgeOutboxStatus status);
}
