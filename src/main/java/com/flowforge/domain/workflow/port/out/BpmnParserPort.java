package com.flowforge.domain.workflow.port.out;

import java.util.Set;

public interface BpmnParserPort {

    /**
     * Parses and validates the given bytes as a BPMN 2.0 document and returns the {@code id} of its
     * main process. Throws {@link com.flowforge.domain.workflow.model.InvalidBpmnException} if the
     * bytes are not well-formed XML, not a valid BPMN 2.0 document, or define no process id.
     */
    String extractProcessDefinitionKey(byte[] bpmnXml);

    /**
     * Returns the {@code decisionTableReferenceKey} of every {@code serviceTask flowable:type="dmn"}
     * in the document (TODO §61 D1) -- empty if none. Only meaningful after a prior successful call
     * to {@link #extractProcessDefinitionKey(byte[])} on the same bytes (which already validates the
     * shape of each such element); does not itself verify the keys are deployed.
     */
    Set<String> extractReferencedDecisionKeys(byte[] bpmnXml);
}
