package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface UpdateDossierFieldsUseCase {
    UpdateDossierFieldsResult update(UpdateDossierFieldsCommand command, AccessContext ctx);

    record UpdateDossierFieldsCommand(
            UUID dossierId, Map<String, Object> fieldsPatch, String updatedBy, Long expectedVersion) {}

    record UpdateDossierFieldsResult(Dossier dossier, List<String> sensitiveFieldsChanged) {}
}
