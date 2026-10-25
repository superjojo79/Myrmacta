package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierRelation;
import be.myrmacta.domain.dossier.model.RelationType;
import java.util.UUID;

public interface CreateDossierRelationUseCase {

    DossierRelation create(CreateDossierRelationCommand command, AccessContext ctx);

    record CreateDossierRelationCommand(
            String tenantId,
            UUID sourceDossierId,
            UUID targetDossierId,
            RelationType type,
            String createdBy,
            String idempotencyKey) {}
}
