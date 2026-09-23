package com.flowforge.domain.workflow.port.out;

import com.flowforge.domain.workflow.model.DecisionTableDeploymentInfo;
import com.flowforge.domain.workflow.model.DecisionTableDeploymentResult;
import com.flowforge.domain.workflow.model.DecisionTableVersionInfo;
import java.util.List;
import java.util.Optional;

public interface DmnDeploymentPort {

    Optional<DecisionTableDeploymentInfo> findDeploymentInfo(String tenantId, String decisionKey);

    /** Latest deployed version of every decision key deployed for this tenant, one entry per key. */
    List<DecisionTableDeploymentInfo> findAllLatestDeployments(String tenantId);

    /** {@code dmnXml} is already validated (parsed, DMN 1.3 schema-checked, JUEL-allowlisted) by the caller. */
    DecisionTableDeploymentResult deploy(String tenantId, String filename, byte[] dmnXml);

    /** Raw DMN 1.3 XML of the latest deployed version for this key, if any version was ever deployed. */
    Optional<byte[]> findDeployedXml(String tenantId, String decisionKey);

    /** Every deployed version of this key, ascending by version number. Empty when the key was never deployed. */
    List<DecisionTableVersionInfo> findAllVersions(String tenantId, String decisionKey);

    /** Raw DMN 1.3 XML of one deployed version. */
    Optional<byte[]> findDeployedXml(String tenantId, String decisionKey, int version);
}
