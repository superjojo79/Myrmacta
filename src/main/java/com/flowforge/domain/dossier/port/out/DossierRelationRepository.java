package com.flowforge.domain.dossier.port.out;

import com.flowforge.domain.dossier.model.DossierRelation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DossierRelationRepository {

    DossierRelation save(DossierRelation relation);

    List<DossierRelation> findByDossier(String tenantId, UUID dossierId);

    Optional<DossierRelation> findByIdempotencyKey(
            String tenantId, UUID sourceDossierId, String idempotencyKey);
}
