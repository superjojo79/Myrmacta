package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.workflow.model.BpmnDraft;
import java.util.Optional;

public interface GetBpmnDraftUseCase {

    Optional<BpmnDraft> get(String processDefinitionKey);
}
