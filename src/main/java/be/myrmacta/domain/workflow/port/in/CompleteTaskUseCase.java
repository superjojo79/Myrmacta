package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import java.util.Map;

public interface CompleteTaskUseCase {

    /**
     * @param actor completing user (TODO §57 H2: must be assignee, or TA/WA). Identity also
     *     populates {@code TaskQuestionAnswer.submittedBy} (D-USERQ-SUBMITTED-BY, TODO §55).
     */
    void complete(String taskId, String dossierId, Map<String, Object> variables, AccessContext.User actor);
}
