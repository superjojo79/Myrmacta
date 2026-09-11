package com.flowforge.domain.workflow.port.out;

import com.flowforge.domain.workflow.model.DecisionTableDeploymentInfo;
import com.flowforge.domain.workflow.model.DecisionTableDeploymentResult;
import java.util.Optional;

public interface DmnDeploymentPort {

    Optional<DecisionTableDeploymentInfo> findDeploymentInfo(String tenantId, String decisionKey);

    /** {@code dmnXml} is already validated (parsed, DMN 1.3 schema-checked, JUEL-allowlisted) by the caller. */
    DecisionTableDeploymentResult deploy(String tenantId, String filename, byte[] dmnXml);

    /** Raw DMN 1.3 XML of the latest deployed version for this key, if any version was ever deployed. */
    Optional<byte[]> findDeployedXml(String tenantId, String decisionKey);
}
