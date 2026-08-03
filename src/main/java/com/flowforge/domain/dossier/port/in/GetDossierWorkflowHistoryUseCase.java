package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import java.util.List;
import java.util.UUID;

public interface GetDossierWorkflowHistoryUseCase {

    List<DossierWorkflowLink> getHistory(UUID dossierId);
}
