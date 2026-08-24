package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.workflow.model.BpmnDraft;
import java.util.Optional;

public interface GetBpmnDraftUseCase {

    Optional<BpmnDraft> get(String processDefinitionKey);
}
