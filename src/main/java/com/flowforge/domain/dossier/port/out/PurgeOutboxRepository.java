package com.flowforge.domain.dossier.port.out;

import com.flowforge.domain.dossier.model.PurgeOutboxRecord;
import java.util.UUID;

public interface PurgeOutboxRepository {

    /** Idempotent insert — a conflict on the UNIQUE {@code dossier_id} constraint is a no-op. */
    void insert(PurgeOutboxRecord record);

    boolean existsByDossierId(UUID dossierId);
}
