package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierType;
import java.util.List;

public interface ListDossierTypesUseCase {
    List<DossierType> list(String tenantId, AccessContext ctx);
}
