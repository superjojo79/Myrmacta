package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.workflow.model.BpmnDeploymentResult;

public interface DeployBpmnProcessUseCase {

    BpmnDeploymentResult deploy(String filename, byte[] bpmnXml);

    /**
     * Tous les contrôles de déploiement (sûreté du parseur, clés DMN, portail, signature, références des briques) sans
     * déployer ni écrire — pour le {@code dryRun} de l'écran et du MCP (TODO §73, parité avec {@link #deploy}).
     *
     * @param effectiveConfig configuration du workflow à prendre en compte : la stockée au déploiement, la
     *     <em>soumise</em> en {@code dryRun}
     */
    void validate(byte[] bpmnXml, java.util.Optional<com.flowforge.domain.workflow.model.WorkflowTypeConfig> effectiveConfig);
}
