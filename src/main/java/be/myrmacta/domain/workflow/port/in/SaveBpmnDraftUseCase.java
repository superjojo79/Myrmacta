package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.workflow.model.BpmnDraft;

public interface SaveBpmnDraftUseCase {

    /**
     * Validates {@code xml} as well-formed BPMN 2.0 and requires its process id to match
     * {@code processDefinitionKey} (D-EDIT-XML-KEY-MATCH), then upserts the draft. Unlike
     * {@link DeployBpmnProcessUseCase}, does <b>not</b> require a matching
     * {@code WorkflowTypeConfig} — a draft may predate the key's configuration
     * (D-EDIT-DRAFT-VALIDATION).
     */
    BpmnDraft save(String processDefinitionKey, String xml, String updatedBy);
}
