package com.flowforge.domain.dossier.port.out;

import com.flowforge.domain.dossier.model.DossierElement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DossierElementRepository {

    DossierElement save(DossierElement element);

    Optional<DossierElement> findByIdAndTenantId(UUID id, String tenantId);

    List<DossierElement> findByDossierIdOrderByAddedAtAsc(UUID dossierId);

    void deleteAllByDossierId(UUID dossierId);
}
