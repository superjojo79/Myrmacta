package com.flowforge.domain.dossier.port.out;

import com.flowforge.domain.dossier.model.Dossier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DossierRepository {
    /** Persists and returns the dossier with post-flush state (e.g. incremented {@code version}). */
    Dossier save(Dossier dossier);

    Optional<Dossier> findById(UUID id);

    Optional<Dossier> findByReference(String tenantId, String reference);

    /**
     * Candidates for retention evaluation (D-SCAN): {@code status = CLOTURE} and
     * {@code closedAt IS NOT NULL}, ordered by {@code closedAt ASC, id ASC}, capped at
     * {@code limit} reads (A-BATCH-COUNT).
     */
    List<Dossier> findClosedForRetention(int limit);
}
