package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;

public interface ListDossierTypesUseCase {
    PageResult<DossierType> list(String tenantId, AccessContext ctx, PageQuery query);
}
