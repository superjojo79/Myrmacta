package be.myrmacta.domain.dossier.port.out;

import be.myrmacta.domain.dossier.model.Dossier;
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
     * {@code closedAt IS NOT NULL} for {@code tenantId}, ordered by {@code closedAt ASC, id ASC},
     * capped at {@code limit} reads (A-BATCH-COUNT). {@code tenantId} is redundant with the
     * schema-per-tenant connection (TODO §37) but kept explicit for defense in depth, matching
     * {@link #findByReference}.
     */
    List<Dossier> findClosedForRetention(String tenantId, int limit);

    /** All dossiers of {@code dossierTypeCode} for {@code tenantId} (schema compatibility scan, no cap V1). */
    List<Dossier> findByDossierTypeCode(String tenantId, String dossierTypeCode);

    void deleteById(UUID id);
}
