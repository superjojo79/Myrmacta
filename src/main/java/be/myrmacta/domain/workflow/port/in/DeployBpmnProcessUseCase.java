package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.workflow.model.BpmnDeploymentResult;

public interface DeployBpmnProcessUseCase {

    BpmnDeploymentResult deploy(String filename, byte[] bpmnXml);

    /**
     * Tous les contrôles de déploiement (sûreté du parseur, clés DMN, portail, signature, références des briques) sans
     * déployer ni écrire — pour le {@code dryRun} de l'écran et du MCP (TODO §73, parité avec {@link #deploy}).
     *
     * @param effectiveConfig configuration du workflow à prendre en compte : la stockée au déploiement, la
     *     <em>soumise</em> en {@code dryRun}
     */
    void validate(byte[] bpmnXml, java.util.Optional<be.myrmacta.domain.workflow.model.WorkflowTypeConfig> effectiveConfig);

    /**
     * TODO §93 (D-DRAFT-REF-STATUS) — mêmes contrôles que {@link #validate}, pour un brouillon proposé par un canal
     * automatisé : une ressource référencée qui existe mais n'est pas encore active est acceptée.
     *
     * @return les références inactives, que la publication refusera tant qu'elles ne sont pas activées
     */
    java.util.List<be.myrmacta.domain.workflow.palette.InactiveBlockReference> validateDraft(
            byte[] bpmnXml, java.util.Optional<be.myrmacta.domain.workflow.model.WorkflowTypeConfig> effectiveConfig);
}
