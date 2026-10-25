package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.util.UUID;

public interface GetDossierWorkflowHistoryUseCase {

    PageResult<DossierWorkflowLink> getHistory(UUID dossierId, PageQuery query);
}
