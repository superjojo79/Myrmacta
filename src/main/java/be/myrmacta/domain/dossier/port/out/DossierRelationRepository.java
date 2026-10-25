package be.myrmacta.domain.dossier.port.out;

import be.myrmacta.domain.dossier.model.DossierRelation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DossierRelationRepository {

    DossierRelation save(DossierRelation relation);

    List<DossierRelation> findByDossier(String tenantId, UUID dossierId);

    Optional<DossierRelation> findByIdempotencyKey(
            String tenantId, UUID sourceDossierId, String idempotencyKey);

    void deleteRelationsTouching(UUID dossierId);
}
