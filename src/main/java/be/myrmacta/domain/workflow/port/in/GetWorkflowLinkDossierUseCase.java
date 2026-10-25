package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.workflow.model.WorkflowLinkDossierView;
import java.util.UUID;

public interface GetWorkflowLinkDossierUseCase {
    WorkflowLinkDossierView getDossier(UUID linkId, AccessContext.User actor);
}
