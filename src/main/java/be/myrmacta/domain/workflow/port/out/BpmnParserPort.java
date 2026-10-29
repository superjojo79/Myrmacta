package be.myrmacta.domain.workflow.port.out;

import be.myrmacta.domain.workflow.model.StartVariableDeclaration;
import java.util.List;
import java.util.Set;

public interface BpmnParserPort {

    /**
     * Parses and validates the given bytes as a BPMN 2.0 document and returns the {@code id} of its
     * main process. Throws {@link be.myrmacta.domain.workflow.model.InvalidBpmnException} if the
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

    /**
     * TODO §68 — tâches externes du document ({@code userTask} portant
     * {@code flowable:portalRecipient}), déjà validées en forme par
     * {@link #extractProcessDefinitionKey(byte[])}. Vide si aucune.
     */
    java.util.List<be.myrmacta.domain.workflow.portal.model.PortalTaskDefinition> extractPortalTasks(
            byte[] bpmnXml);

    /**
     * TODO §72 — blocs « Demander une signature » du document ({@code serviceTask}
     * {@code ${signatureRequestTask}} portant des attributs {@code flowable:signature*} et des
     * éléments {@code flowable:signer}), déjà validés en forme par
     * {@link #extractProcessDefinitionKey(byte[])}. Les bornes tenant (fonctionnalité active, portail,
     * nombre de signataires, échéance) sont vérifiées au déploiement, pas ici. Vide si aucun.
     */
    java.util.List<be.myrmacta.domain.signature.model.SignatureBlockDefinition> extractSignatureBlocks(
            byte[] bpmnXml);

    /**
     * TODO §73 — ressources du tenant référencées par les attributs de brique ({@code flowable:documentType},
     * {@code connectorInstanceId}, …), déjà validés en forme par {@link #extractProcessDefinitionKey(byte[])}.
     * L'existence est vérifiée par le use case de déploiement. Vide si aucune.
     */
    java.util.List<be.myrmacta.domain.workflow.palette.BlockParameterUsage> extractBlockReferences(byte[] bpmnXml);

    /** TODO §106 (issue #211, lot B) — briques « Pré-vérification IA » du BPMN (forme déjà validée par le parseur). */
    default java.util.List<be.myrmacta.domain.precheck.model.PrecheckBlock> extractPrecheckBlocks(byte[] bpmnXml) {
        return java.util.List.of();
    }

    /**
     * TODO §111 (issue #187, D-START-DECLARED) — variables de démarrage déclarées sur le processus dont
     * l'identifiant est {@code processDefinitionKey} ({@code flowable:startVariable}). Un fichier peut en
     * contenir plusieurs : ce n'est pas le premier pool. Vide si ce processus n'en déclare aucune.
     */
    List<StartVariableDeclaration> extractStartVariables(byte[] bpmnXml, String processDefinitionKey);
}
