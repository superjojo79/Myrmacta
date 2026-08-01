package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;

public interface RevokeDossierTypeAccessUseCase {
    void revoke(String tenantId, String dossierTypeCode, String subjectUserId, AccessContext.User actor);
}
