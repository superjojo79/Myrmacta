package be.myrmacta.domain.workflow.port.out;

import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import java.util.List;

public interface WorkflowParticipantPort {

    List<String> findParticipantUserIds(DossierWorkflowLink link);
}
