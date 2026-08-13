package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierPage;
import com.flowforge.domain.dossier.model.SearchDossiersQuery;

public interface SearchDossiersUseCase {
    DossierPage search(String tenantId, SearchDossiersQuery query, AccessContext ctx);
}
