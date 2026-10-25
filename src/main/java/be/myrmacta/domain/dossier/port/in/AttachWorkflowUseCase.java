package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AbortReservedReason;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import java.util.UUID;

public interface AttachWorkflowUseCase {

    DossierWorkflowLink reserve(UUID dossierId, String processDefinitionKey, String triggerRef, String startedBy);

    DossierWorkflowLink reserveOrResume(UUID dossierId, String processDefinitionKey, String triggerRef, String startedBy);

    void confirmStarted(UUID linkId, String processInstanceId);

    void abortReserved(UUID linkId, AbortReservedReason reason);
}
