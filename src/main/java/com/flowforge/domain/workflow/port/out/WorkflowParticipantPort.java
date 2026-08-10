package com.flowforge.domain.workflow.port.out;

import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import java.util.List;

public interface WorkflowParticipantPort {

    List<String> findParticipantUserIds(DossierWorkflowLink link);
}
