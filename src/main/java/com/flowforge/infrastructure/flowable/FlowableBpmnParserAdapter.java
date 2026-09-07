package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.workflow.AllowedBpmnDelegates;
import com.flowforge.domain.workflow.model.InvalidBpmnException;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.List;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.HasExecutionListeners;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ScriptTask;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.UserTask;
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
        for (Process process : model.getProcesses()) {
            assertSafeProcess(process);
        }
        return mainProcess.getId();
    }

    private static void assertSafeProcess(Process process) {
        assertSafeListeners("process " + process.getId(), process.getExecutionListeners());
        walk(process.getFlowElements());
    }

    private static void walk(Collection<FlowElement> elements) {
        if (elements == null) {
            return;
        }
        for (FlowElement element : elements) {
            assertSafeElement(element);
            if (element instanceof SubProcess subProcess) {
                walk(subProcess.getFlowElements());
            }
        }
    }

    private static void assertSafeElement(FlowElement element) {
        if (element instanceof HasExecutionListeners hasListeners) {
            assertSafeListeners(element.getId(), hasListeners.getExecutionListeners());
        }
        if (element instanceof UserTask userTask) {
            assertSafeListeners(element.getId() + " taskListeners", userTask.getTaskListeners());
        }
        if (element instanceof ScriptTask) {
            throw new InvalidBpmnException("BPMN scriptTask is not allowed: " + element.getId());
        }
        if (element instanceof ServiceTask serviceTask) {
            assertSafeServiceTask(serviceTask);
        }
    }

    private static void assertSafeServiceTask(ServiceTask serviceTask) {
        if (serviceTask.getType() != null && !serviceTask.getType().isBlank()) {
            throw new InvalidBpmnException(
                    "BPMN built-in serviceTask type is not allowed: " + serviceTask.getType());
        }
        String implementationType = serviceTask.getImplementationType();
        if (implementationType == null || implementationType.isBlank()) {
            throw new InvalidBpmnException(
                    "BPMN serviceTask must use flowable:delegateExpression: " + serviceTask.getId());
        }
        if (!ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION.equals(implementationType)) {
            throw new InvalidBpmnException(
                    "BPMN serviceTask implementation type is not allowed: " + implementationType);
        }
        if (!AllowedBpmnDelegates.isAllowedDelegateExpression(serviceTask.getImplementation())) {
            throw new InvalidBpmnException(
                    "BPMN delegateExpression is not in the allowlist: " + serviceTask.getImplementation());
        }
    }

    private static void assertSafeListeners(String owner, List<FlowableListener> listeners) {
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        for (FlowableListener listener : listeners) {
            String type = listener.getImplementationType();
            if (ImplementationType.IMPLEMENTATION_TYPE_SCRIPT.equals(type)
                    || listener.getScriptInfo() != null) {
                throw new InvalidBpmnException("BPMN script listener is not allowed: " + owner);
            }
            if (ImplementationType.IMPLEMENTATION_TYPE_CLASS.equals(type)
                    || ImplementationType.IMPLEMENTATION_TYPE_EXPRESSION.equals(type)) {
                throw new InvalidBpmnException("BPMN listener implementation type is not allowed: " + type);
            }
            if (ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION.equals(type)
                    && !AllowedBpmnDelegates.isAllowedDelegateExpression(listener.getImplementation())) {
                throw new InvalidBpmnException(
                        "BPMN listener delegateExpression is not in the allowlist: "
                                + listener.getImplementation());
            }
        }
    }
}
