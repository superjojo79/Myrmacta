package com.flowforge.domain.workflow.palette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.workflow.model.InvalidBpmnException;
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
                "notificationWebhookConfigId", "childDossierTypeCode", "childDossierLabel", "workflowProcessKey",
                "relationType", "targetDossierStatus", "targetWorkflowStatus", "formula", "formulaResult");
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
