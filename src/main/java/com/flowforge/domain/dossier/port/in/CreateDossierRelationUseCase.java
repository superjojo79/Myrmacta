package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierRelation;
import com.flowforge.domain.dossier.model.RelationType;
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
