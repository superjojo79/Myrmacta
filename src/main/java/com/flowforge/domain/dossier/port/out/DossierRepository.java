package com.flowforge.domain.dossier.port.out;

import com.flowforge.domain.dossier.model.Dossier;
import java.util.Optional;
import java.util.UUID;

public interface DossierRepository {
    /** Persists and returns the dossier with post-flush state (e.g. incremented {@code version}). */
    Dossier save(Dossier dossier);

    Optional<Dossier> findById(UUID id);

    Optional<Dossier> findByReference(String tenantId, String reference);
}
