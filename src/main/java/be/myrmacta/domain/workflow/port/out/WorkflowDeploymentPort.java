package be.myrmacta.domain.workflow.port.out;

import be.myrmacta.domain.workflow.model.BpmnDeploymentResult;
import be.myrmacta.domain.workflow.model.ProcessDeploymentInfo;
import java.util.Optional;

public interface WorkflowDeploymentPort {

    Optional<ProcessDeploymentInfo> findDeploymentInfo(String tenantId, String processDefinitionKey);

    /** {@code bpmnXml} is already validated (parsed, BPMN 2.0 schema-checked) by the caller. */
    BpmnDeploymentResult deploy(String tenantId, String filename, byte[] bpmnXml);

    /** Raw BPMN 2.0 XML of the latest deployed version for this key, if any version was ever deployed. */
    Optional<byte[]> findDeployedXml(String tenantId, String processDefinitionKey);
}
