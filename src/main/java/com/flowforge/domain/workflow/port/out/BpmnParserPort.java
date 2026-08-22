package com.flowforge.domain.workflow.port.out;

public interface BpmnParserPort {

    /**
     * Parses and validates the given bytes as a BPMN 2.0 document and returns the {@code id} of its
     * main process. Throws {@link com.flowforge.domain.workflow.model.InvalidBpmnException} if the
     * bytes are not well-formed XML, not a valid BPMN 2.0 document, or define no process id.
     */
    String extractProcessDefinitionKey(byte[] bpmnXml);
}
