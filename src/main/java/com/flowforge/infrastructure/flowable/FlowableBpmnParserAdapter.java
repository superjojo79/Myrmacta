package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.workflow.model.InvalidBpmnException;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import java.io.ByteArrayInputStream;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.springframework.stereotype.Component;

@Component
class FlowableBpmnParserAdapter implements BpmnParserPort {

    @Override
    public String extractProcessDefinitionKey(byte[] bpmnXml) {
        BpmnModel model;
        try {
            model = new BpmnXMLConverter().convertToBpmnModel(() -> new ByteArrayInputStream(bpmnXml), true, false);
        } catch (Exception ex) {
            throw new InvalidBpmnException("File is not a valid BPMN 2.0 document: " + ex.getMessage(), ex);
        }
        Process mainProcess = model.getMainProcess();
        if (mainProcess == null || mainProcess.getId() == null || mainProcess.getId().isBlank()) {
            throw new InvalidBpmnException("BPMN document does not define a process with an id");
        }
        return mainProcess.getId();
    }
}
