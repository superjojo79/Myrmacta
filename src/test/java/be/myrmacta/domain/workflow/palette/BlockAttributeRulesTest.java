package be.myrmacta.domain.workflow.palette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** S-PARSE-2..4, S-PARSE-6, S-PARSE-9, S-RT-6 — règles de forme des attributs de brique (TODO §73, M3). */
class BlockAttributeRulesTest {

    private static final String UUID_OK = UUID.randomUUID().toString();

    @Test
    void validAttributesOfEachDelegateAreAccepted() {
        BlockAttributeRules.validate("a", "documentGenerationTask",
                Map.of("documentType", "facture", "documentVariant", "premium", "documentLocale", "fr"));
        BlockAttributeRules.validate("a", "connectorServiceTask", Map.of("connectorInstanceId", UUID_OK));
        BlockAttributeRules.validate("a", "notificationDispatchTask", Map.of(
                "notificationChannels", "EMAIL,BROWSER", "notificationTo", "a@example.org", "notificationSubject", "S",
                "notificationBody", "Ligne 1\nLigne 2", "notificationUserId", "u1", "notificationWebhookConfigId", UUID_OK));
        BlockAttributeRules.validate("a", "createChildDossierTask", Map.of(
                "childDossierTypeCode", "ENFANT", "childDossierLabel", "Suite", "workflowProcessKey", "suite",
                "relationType", "PARENT_CHILD"));
        BlockAttributeRules.validate("a", "updateDossierStatusTask", Map.of("targetDossierStatus", "CLOTURE"));
        BlockAttributeRules.validate("a", "updateWorkflowLinkStatusTask", Map.of("targetWorkflowStatus", "EN_INSTRUCTION"));
        BlockAttributeRules.validate("a", "attachGeneratedDocumentTask", Map.of());
    }

    /** TODO §93 — notificationTo : adresse littérale, ou source dossierField:/variable: bien formée. */
    @Test
    void notificationToAcceptsALiteralOrAWellFormedSource() {
        for (String ok : List.of("a@example.org", "dossierField:email", "variable:adresse_demandeur")) {
            BlockAttributeRules.validate("a", "notificationDispatchTask",
                    Map.of("notificationChannels", "EMAIL", "notificationTo", ok));
        }
        for (String bad : List.of("dossierField:", "dossierField:a.b", "variable:1x", "variable:", "dossierField:e-mail")) {
            assertThatThrownBy(() -> BlockAttributeRules.validate("act", "notificationDispatchTask",
                    Map.of("notificationChannels", "EMAIL", "notificationTo", bad)))
                    .isInstanceOf(InvalidBpmnException.class)
                    .hasMessageContaining("notificationTo").hasMessageContaining("dossierField:<name>");
        }
    }

    /** Issue #183 — notificationAttachment : source dossierField:/variable: obligatoire, jamais un storageRef littéral. */
    @Test
    void notificationAttachmentMustBeAWellFormedSource() {
        for (String ok : List.of("variable:generatedDocumentId", "dossierField:decision_pdf")) {
            BlockAttributeRules.validate("a", "notificationDispatchTask", Map.of("notificationChannels", "EMAIL",
                    "notificationTo", "dossierField:email", "notificationAttachment", ok));
        }
        for (String bad : List.of("tenant/abc.pdf", "variable:", "variable:a.b", "dossierField:e-mail")) {
            assertThatThrownBy(() -> BlockAttributeRules.validate("act", "notificationDispatchTask", Map.of(
                    "notificationChannels", "EMAIL", "notificationTo", "dossierField:email", "notificationAttachment", bad)))
                    .as(bad).isInstanceOf(InvalidBpmnException.class)
                    .hasMessageContaining("notificationAttachment").hasMessageContaining("dossierField:<name>");
        }
    }

    /** Issue #183 (D-NOTIF-ATTACH-NAME) — nom de fichier : alphabet restreint, sans chemin ni point initial. */
    @Test
    void notificationAttachmentNameIsAPlainFileName() {
        for (String ok : List.of("autorisation.pdf", "Décision riverain (2026)", "d_1-2.pdf", "l'autorisation")) {
            BlockAttributeRules.validate("a", "notificationDispatchTask", withAttachment("notificationAttachmentName", ok));
        }
        for (String bad : List.of("../x.pdf", "a/b.pdf", "a\\b.pdf", ".pdf", "a\"b.pdf", "a;b", "x".repeat(101))) {
            assertThatThrownBy(() -> BlockAttributeRules.validate("act", "notificationDispatchTask",
                    withAttachment("notificationAttachmentName", bad)))
                    .as(bad).isInstanceOf(InvalidBpmnException.class).hasMessageContaining("notificationAttachmentName");
        }
    }

    /**
     * Revue adversariale #183 (M1, L5) — avec une pièce jointe, destinataire et canal EMAIL sont écrits dans le BPMN : une
     * variable posée par un utilisateur ne peut pas diriger le PDF vers une autre adresse. Un nom sans pièce jointe est refusé.
     */
    @Test
    void anAttachmentNeedsALiteralRecipientAndTheEmailChannelInTheBpmn() {
        Map<String, String> noRecipient = Map.of("notificationChannels", "EMAIL", "notificationAttachment", "variable:doc");
        Map<String, String> noChannels = Map.of("notificationTo", "dossierField:email", "notificationAttachment", "variable:doc");
        Map<String, String> noEmail = Map.of("notificationChannels", "BROWSER,WEBHOOK", "notificationTo", "a@example.org",
                "notificationAttachment", "variable:doc");
        for (Map<String, String> bad : List.of(noRecipient, noChannels, noEmail)) {
            assertThatThrownBy(() -> BlockAttributeRules.validate("act", "notificationDispatchTask", bad))
                    .as(bad.toString()).isInstanceOf(InvalidBpmnException.class)
                    .hasMessageContaining("notificationAttachment");
        }
        assertThatThrownBy(() -> BlockAttributeRules.validate("act", "notificationDispatchTask",
                Map.of("notificationChannels", "EMAIL", "notificationAttachmentName", "a.pdf")))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("notificationAttachmentName");
    }

    private static Map<String, String> withAttachment(String name, String value) {
        return Map.of("notificationChannels", "EMAIL", "notificationTo", "dossierField:email",
                "notificationAttachment", "variable:doc", name, value);
    }

    @Test
    void noBlockAttributeMeansNothingToCheck() {
        BlockAttributeRules.validate("a", "updateDossierStatusTask", Map.of());
        BlockAttributeRules.validate("a", null, Map.of());
    }

    @Test
    void anAttributeOfAnotherBlockIsRefused() {
        assertThatThrownBy(() -> BlockAttributeRules.validate("act1", "updateDossierStatusTask",
                Map.of("documentType", "facture")))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("act1").hasMessageContaining("documentType");
        assertThatThrownBy(() -> BlockAttributeRules.validate("act2", "attachGeneratedDocumentTask",
                Map.of("targetDossierStatus", "CLOTURE")))
                .isInstanceOf(InvalidBpmnException.class);
    }

    @Test
    void aBlockAttributeOnAnActivityWithoutTheDelegateIsRefused() {
        assertThatThrownBy(() -> BlockAttributeRules.validate("u1", null, Map.of("targetDossierStatus", "CLOTURE")))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("targetDossierStatus");
    }

    @Test
    void nonLiteralValuesAreRefusedEverywhere() {
        for (String bad : List.of("${x}", "#{x}", "a${b}", " #{ }")) {
            assertThatThrownBy(() -> BlockAttributeRules.validate("a", "notificationDispatchTask",
                    Map.of("notificationBody", bad)))
                    .as(bad).isInstanceOf(InvalidBpmnException.class).hasMessageContaining("literal");
        }
    }

    @Test
    void formsAreChecked() {
        assertRefused("connectorServiceTask", "connectorInstanceId", "pas-un-uuid");
        assertRefused("notificationDispatchTask", "notificationWebhookConfigId", "1234");
        assertRefused("updateDossierStatusTask", "targetDossierStatus", "INCONNU");
        assertRefused("createChildDossierTask", "relationType", "LINKED");
        assertRefused("notificationDispatchTask", "notificationChannels", "EMAIL,SMS");
        assertRefused("documentGenerationTask", "documentType", "avec espace");
        assertRefused("documentGenerationTask", "documentType", "../secret");
        assertRefused("notificationDispatchTask", "notificationBody", "ctl\u0001char");
        assertRefused("updateDossierStatusTask", "targetDossierStatus", "  ");
    }

    @Test
    void lengthsAreBounded() {
        BlockAttributeRules.validate("a", "notificationDispatchTask", Map.of("notificationBody", "x".repeat(4000)));
        assertRefused("notificationDispatchTask", "notificationBody", "x".repeat(4001));
        assertRefused("notificationDispatchTask", "notificationSubject", "x".repeat(201));
        assertRefused("documentGenerationTask", "documentLocale", "x".repeat(21));
    }

    @Test
    void referenceUsagesListEveryReferencedResourceOfTheSixDelegates() {
        List<BlockParameterUsage> usages = BlockAttributeRules.referenceUsages("act", "createChildDossierTask", Map.of(
                "childDossierTypeCode", "ENFANT", "childDossierLabel", "Suite", "workflowProcessKey", "suite",
                "relationType", "PARENT_CHILD"));

        assertThat(usages).extracting(BlockParameterUsage::parameter, BlockParameterUsage::valueSource,
                        BlockParameterUsage::value)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("childDossierTypeCode", "dossierTypes", "ENFANT"),
                        org.assertj.core.groups.Tuple.tuple("workflowProcessKey", "workflowTypes", "suite"));
        assertThat(usages).allSatisfy(u -> assertThat(u.activityId()).isEqualTo("act"));
        assertThat(BlockAttributeRules.referenceUsages("a", "updateDossierStatusTask",
                Map.of("targetDossierStatus", "CLOTURE"))).isEmpty();
    }

    @Test
    void managedAttributeNamesAreExactlyThoseOfTheBlockDelegates() {
        assertThat(BlockAttributeRules.MANAGED_ATTRIBUTES).containsExactlyInAnyOrder(
                "documentType", "documentVariant", "documentLocale", "connectorInstanceId", "notificationChannels",
                "notificationTo", "notificationSubject", "notificationBody", "notificationUserId",
                "notificationWebhookConfigId", "notificationAttachment", "notificationAttachmentName", "childDossierTypeCode", "childDossierLabel", "workflowProcessKey",
                "relationType", "targetDossierStatus", "targetWorkflowStatus", "formula", "formulaResult", "fieldMapping",
                "searchCriteria", "searchCountResult", "searchFirstReferenceResult", "precheckPieces", "precheckFields",
                "precheckQuestions", "precheckRules", "precheckResult", "connectorRequest");
    }

    /** TODO §102 (lot B, R1, R4, R5) — brique « Rechercher des dossiers » : forme validée au déploiement. */
    @Test
    void theDossierSearchBlockIsValidatedAtDeployment() {
        BlockAttributeRules.validate("s", "dossierSearchTask", Map.of(
                "searchCriteria", "email = dossierField:email\nplaque = variable:plaque",
                "searchCountResult", "nbDemandes", "searchFirstReferenceResult", "premiereDemande"));
        // Littéraux seulement : sans les trois attributs, une variable posée à la complétion ne doit pas les remplacer.
        for (String missing : List.of("searchCriteria", "searchCountResult", "searchFirstReferenceResult")) {
            Map<String, String> attributes = new java.util.HashMap<>(Map.of("searchCriteria", "email = dossierField:email",
                    "searchCountResult", "n", "searchFirstReferenceResult", "r"));
            attributes.remove(missing);
            assertThatThrownBy(() -> BlockAttributeRules.validate("s", "dossierSearchTask", attributes))
                    .as(missing).isInstanceOf(InvalidBpmnException.class).hasMessageContaining(missing);
        }
        assertThatThrownBy(() -> BlockAttributeRules.validate("s", "dossierSearchTask", Map.of(
                "searchCriteria", "email = \"x\"", "searchCountResult", "n", "searchFirstReferenceResult", "r")))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("dossierField:");
        for (String reserved : List.of("dossierId", "connectorInput", "formula", "searchCriteria", "loopCounter")) {
            assertThatThrownBy(() -> BlockAttributeRules.validate("s", "dossierSearchTask", Map.of(
                    "searchCriteria", "email = dossierField:email", "searchCountResult", reserved,
                    "searchFirstReferenceResult", "r")))
                    .as(reserved).isInstanceOf(InvalidBpmnException.class).hasMessageContaining("reserved");
        }
        assertThatThrownBy(() -> BlockAttributeRules.validate("s", "dossierSearchTask", Map.of(
                "searchCriteria", "email = dossierField:email", "searchCountResult", "n", "searchFirstReferenceResult", "n")))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("distinct");
        assertThatThrownBy(() -> BlockAttributeRules.validate("s", "businessExpressionTask", Map.of(
                "formula", "1", "formulaResult", "x", "searchCountResult", "n")))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("not allowed");
    }

    private static void assertRefused(String delegate, String attribute, String value) {
        assertThatThrownBy(() -> BlockAttributeRules.validate("a", delegate, Map.of(attribute, value)))
                .as(attribute + "=" + value).isInstanceOf(InvalidBpmnException.class);
    }

    @Test
    void surroundingWhitespaceAndLineBreaksAreRefusedBecauseExecutionReadsTheValueAsIs() {
        // revue I2 : la validation ne « tolère » plus ce que l'exécution ferait échouer
        for (String bad : List.of(" CLOTURE", "CLOTURE ", "CLOTURE\n", "CLO\tTURE")) {
            assertThatThrownBy(() -> BlockAttributeRules.validate("a", "updateDossierStatusTask",
                    Map.of("targetDossierStatus", bad)))
                    .isInstanceOf(InvalidBpmnException.class);
        }
        assertThatThrownBy(() -> BlockAttributeRules.validate("a", "documentGenerationTask",
                Map.of("documentLocale", "fr ")))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("whitespace");
        // le corps du message reste multi-lignes
        BlockAttributeRules.validate("a", "notificationDispatchTask",
                Map.of("notificationChannels", "EMAIL", "notificationBody", "L1\nL2\n"));
    }

    // ---- TODO §75 (LC2) : brique « Évaluer une expression » ---------------------------------------------------------

    @Test
    void theExpressionBlockAcceptsAFormulaAndAResultVariableOnItsDelegateOnly() {
        BlockAttributeRules.validate("a", "businessExpressionTask",
                Map.of("formula", "dossier.fields.montant > 5000\n and dossier.fields.pays = \"FR\"", "formulaResult", "gros_dossier"));
        assertThatThrownBy(() -> BlockAttributeRules.validate("a", "updateDossierStatusTask",
                        Map.of("formula", "1 + 1")))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("formula");
        assertThatThrownBy(() -> BlockAttributeRules.validate("a", null, Map.of("formulaResult", "x")))
                .isInstanceOf(InvalidBpmnException.class);
    }

    @Test
    void theExpressionDelegateRequiresBothLiteralAttributesBecauseItNeverFallsBackOnAVariable() {
        assertThatThrownBy(() -> BlockAttributeRules.validate("a", "businessExpressionTask", Map.of("formulaResult", "x")))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("formula").hasMessageContaining("required");
        assertThatThrownBy(() -> BlockAttributeRules.validate("a", "businessExpressionTask", Map.of("formula", "1")))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("formulaResult");
        assertThatThrownBy(() -> BlockAttributeRules.validate("a", "businessExpressionTask", Map.of()))
                .isInstanceOf(InvalidBpmnException.class);
    }

    @Test
    void aFormulaIsNeverAnExpressionInTheUelSenseAndIsBoundedInLength() {
        assertThatThrownBy(() -> BlockAttributeRules.validate("a", "businessExpressionTask",
                        Map.of("formula", "${1+1}", "formulaResult", "x")))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("literal");
        assertThatThrownBy(() -> BlockAttributeRules.validate("a", "businessExpressionTask",
                        Map.of("formula", "1".repeat(2001), "formulaResult", "x")))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("2000");
    }

    @Test
    void theResultVariableMustNotShadowAReservedVariableOrABlockParameterName() {
        // Un delegate relit ses paramètres en repli depuis une variable de même nom : une expression ne doit pas
        // pouvoir piloter le statut cible, le connecteur ou le destinataire d'une autre brique.
        for (String reserved : List.of("dossierId", "connectorResult", "connectorInput", "now", "dossier", "variables",
                "targetDossierStatus", "connectorInstanceId", "documentType", "notificationTo", "formula", "formulaResult",
                // Revue adversariale LC2 : variables de la plateforme et de Flowable (multi-instance, exécution, tâche).
                "dossierFields", "tenantId", "dossierWorkflowLinkId", "nrOfInstances", "nrOfActiveInstances",
                "nrOfCompletedInstances", "loopCounter", "execution", "task", "initiator", "authenticatedUserId")) {
            assertThatThrownBy(() -> BlockAttributeRules.validate("a", "businessExpressionTask",
                            Map.of("formula", "1", "formulaResult", reserved)))
                    .as(reserved).isInstanceOf(InvalidBpmnException.class).hasMessageContaining("reserved");
        }
        for (String malformed : List.of("1abc", "a-b", "a b", "a.b", "_x", "a".repeat(61))) {
            assertThatThrownBy(() -> BlockAttributeRules.validate("a", "businessExpressionTask",
                            Map.of("formula", "1", "formulaResult", malformed)))
                    .as(malformed).isInstanceOf(InvalidBpmnException.class);
        }
    }
}
