package be.myrmacta.domain.workflow.port.in;

import java.util.UUID;

public interface UpdateWorkflowLinkStatusUseCase {

    void update(UUID linkId, String status, String activityId);
}
