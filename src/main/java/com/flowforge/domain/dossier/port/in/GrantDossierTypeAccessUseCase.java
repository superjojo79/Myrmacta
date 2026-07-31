package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierPermission;

public interface GrantDossierTypeAccessUseCase {
    void grant(GrantCommand command, AccessContext.User actor);

    record GrantCommand(
            String tenantId, String dossierTypeCode, String subjectUserId, DossierPermission permission) {}
}
