package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AbortReservedReason;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import java.util.UUID;

public interface AttachWorkflowUseCase {

    DossierWorkflowLink reserve(UUID dossierId, String processDefinitionKey, String triggerRef);

    DossierWorkflowLink reserveOrResume(UUID dossierId, String processDefinitionKey, String triggerRef);

    void confirmStarted(UUID linkId, String processInstanceId);

    void abortReserved(UUID linkId, AbortReservedReason reason);
}
