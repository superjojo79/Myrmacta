package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import java.util.Map;

public interface CreateDossierUseCase {
    Dossier create(CreateDossierCommand command, AccessContext ctx);

    record CreateDossierCommand(
            String tenantId,
            String dossierTypeCode,
            String label,
            Map<String, Object> fields,
            String createdBy) {}
}
