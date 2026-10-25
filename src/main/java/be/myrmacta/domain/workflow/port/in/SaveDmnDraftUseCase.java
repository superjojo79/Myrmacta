package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.workflow.model.DmnDraft;

public interface SaveDmnDraftUseCase {

    /**
     * Validates {@code xml} as a well-formed, JUEL-allowlisted DMN 1.3 decision table (mirrors
     * {@link SaveBpmnDraftUseCase} D-EDIT-XML-KEY-MATCH) and requires its decision id to match
     * {@code decisionKey}, then upserts the draft.
     */
    DmnDraft save(String decisionKey, String xml, String updatedBy);
}
