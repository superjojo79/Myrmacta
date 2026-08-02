package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.workflow.port.in.UpdateWorkflowLinkStatusUseCase;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("updateWorkflowLinkStatusTask")
class UpdateWorkflowLinkStatusDelegate implements JavaDelegate {

    private final UpdateWorkflowLinkStatusUseCase useCase;

    UpdateWorkflowLinkStatusDelegate(UpdateWorkflowLinkStatusUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Object linkId = execution.getVariable("dossierWorkflowLinkId");
        Object status = execution.getVariable("targetWorkflowStatus");
        if (linkId == null || status == null) {
            throw new IllegalArgumentException("dossierWorkflowLinkId and targetWorkflowStatus variables are required");
        }
        useCase.update(UUID.fromString(linkId.toString()), status.toString(), execution.getCurrentActivityId());
    }
}
