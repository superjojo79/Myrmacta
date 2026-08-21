package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierElementView;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.util.UUID;

public interface ListDossierElementsUseCase {

    PageResult<DossierElementView> list(UUID dossierId, AccessContext ctx, PageQuery query);
}
