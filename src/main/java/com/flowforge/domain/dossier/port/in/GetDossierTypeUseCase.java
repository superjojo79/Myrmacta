package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierType;

public interface GetDossierTypeUseCase {
    DossierType getByCode(String tenantId, String code, AccessContext ctx);
}
