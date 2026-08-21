package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.util.UUID;

public interface GetDossierWorkflowHistoryUseCase {

    PageResult<DossierWorkflowLink> getHistory(UUID dossierId, PageQuery query);
}
