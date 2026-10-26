package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import be.myrmacta.domain.workflow.palette.BlockParameter;
import be.myrmacta.domain.workflow.palette.BlockParameterUsage;
import be.myrmacta.domain.workflow.palette.BuildingBlock;
import be.myrmacta.domain.workflow.palette.BuildingBlockCatalog;
import be.myrmacta.domain.workflow.palette.ParameterBinding;
import be.myrmacta.domain.workflow.palette.ParameterType;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * TODO §73 — S-PARSE-1 (conformité catalogue ↔ parseur, US-8), S-PARSE-2..4, 6, S-PARSE-8 : la validation des
 * attributs de brique au déploiement est dérivée du catalogue.
 */
class FlowableBpmnParserAdapterBlockTest {

    private final FlowableBpmnParserAdapter adapter = new FlowableBpmnParserAdapter();

    static java.util.stream.Stream<BuildingBlock> blocks() {
        return BuildingBlockCatalog.all().stream();
    }

    /** XML minimal d'une brique avec des valeurs d'exemple pour chacun de ses paramètres. */
    static String sampleXml(BuildingBlock block) {
        StringBuilder attributes = new StringBuilder();
        StringBuilder children = new StringBuilder();
        String delegate = block.marker().delegate();
        String type = block.marker().serviceTaskType();
        String formKey = null;
        if (delegate != null) {
            attributes.append(" flowable:delegateExpression=\"${").append(delegate).append("}\"");
        }
        if (type != null) {
            attributes.append(" flowable:type=\"").append(type).append("\"");
        }
        if ("aiPrecheckTask".equals(delegate)) {
            attributes.append(" flowable:async=\"true\""); // TODO §106 (D-PRECHECK-BLOCK) : toujours asynchrone
        }
        for (BlockParameter p : block.parameters()) {
            String value = sampleValue(p);
            switch (p.binding()) {
                case ATTRIBUTE -> attributes.append(" flowable:").append(p.name()).append("=\"").append(value).append("\"");
                case FIELD_ELEMENT -> children.append("<flowable:field name=\"").append(p.name())
                        .append("\"><flowable:string>").append(value).append("</flowable:string></flowable:field>");
                case FORM_KEY_SUFFIX -> formKey = block.marker().formKeyPrefix() + value;
                case SIGNER_ELEMENTS -> children.append(
                        "<flowable:signer source=\"dossierField:email\" kind=\"EXTERNAL\" required=\"true\" name=\"Client\"/>");
            }
        }
        if (formKey != null) {
            attributes.append(" flowable:formKey=\"").append(formKey).append("\"");
        }
        String tag = block.element().name().equals("USER_TASK") ? "userTask" : "serviceTask";
        return "<" + tag + " id=\"act\"" + attributes + ">"
                + (children.isEmpty() ? "" : "<extensionElements>" + children + "</extensionElements>")
                + "</" + tag + ">";
    }

    private static String sampleValue(BlockParameter p) {
        return switch (p.type()) {
            case ENUM -> p.enumValues().getFirst();
            case ENUM_MULTI -> String.join(",", p.enumValues().subList(0, 2));
            case INTEGER -> String.valueOf(p.min());
            case SOURCE_REF -> "variable:generatedDocumentId";
            case REFERENCE -> "connectorInstances".equals(p.valueSource()) || "notificationWebhooks".equals(p.valueSource())
                    ? UUID.randomUUID().toString() : "key1";
            case SIGNERS -> "";
            case TEXT -> "documentVariant".equals(p.name()) || "documentLocale".equals(p.name()) ? "fr"
                    : "fieldMapping".equals(p.name()) ? "score = connectorResult.json.score"
                    : "searchCriteria".equals(p.name()) ? "email = dossierField:email"
                    : "searchCountResult".equals(p.name()) ? "nbTrouves"
                    : "searchFirstReferenceResult".equals(p.name()) ? "premierTrouve"
                    : "precheckPieces".equals(p.name()) ? "preuve : obligatoire, lire"
                    : "precheckFields".equals(p.name()) ? "commune"
                    : "precheckQuestions".equals(p.name()) ? "q = Question ?"
                    : "precheckRules".equals(p.name()) ? "variable:doublons &gt; 0 =&gt; INCOHERENCE : Doublon."
                    : "precheckResult".equals(p.name()) ? "precheck" : "texte";
        };
    }

    private static byte[] bpmn(String element) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://myrmacta/test">
                  <process id="testProcess" isExecutable="true">
                    <startEvent id="start"/>
                    %s
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="act"/>
                    <sequenceFlow sourceRef="act" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(element).getBytes(StandardCharsets.UTF_8);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blocks")
    void everyCatalogBlockProducesXmlTheParserAccepts(BuildingBlock block) {
        // S-PARSE-1 : le catalogue et l'allowlist ne divergent jamais
        assertThat(adapter.extractProcessDefinitionKey(bpmn(sampleXml(block)))).isEqualTo("testProcess");
    }

    private void assertRefused(String element, String messagePart) {
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(bpmn(element)))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining(messagePart);
    }

    private static String service(String delegate, String attributes) {
        return "<serviceTask id=\"act\" flowable:delegateExpression=\"${" + delegate + "}\" " + attributes + "/>";
    }

    @Test
    void attributeOfAnotherBlockIsRefused() {
        assertRefused(service("updateDossierStatusTask", "flowable:documentType=\"facture\""), "documentType");
    }

    @Test
    void blockAttributeOnAUserTaskIsRefused() {
        assertRefused("<userTask id=\"act\" flowable:targetDossierStatus=\"CLOTURE\"/>", "targetDossierStatus");
    }

    @Test
    void expressionsAreRefusedInEveryAttribute() {
        assertRefused(service("updateDossierStatusTask", "flowable:targetDossierStatus=\"${status}\""), "literal");
        assertRefused(service("notificationDispatchTask", "flowable:notificationBody=\"a #{b}\""), "literal");
    }

    @Test
    void invalidFormsAreRefusedWithTheActivityAndAttribute() {
        assertRefused(service("connectorServiceTask", "flowable:connectorInstanceId=\"nope\""), "connectorInstanceId");
        assertRefused(service("createChildDossierTask", "flowable:relationType=\"LINKED\""), "relationType");
        assertRefused(service("updateDossierStatusTask", "flowable:targetDossierStatus=\"???\""), "targetDossierStatus");
        assertRefused(service("notificationDispatchTask", "flowable:notificationChannels=\"SMS\""), "notificationChannels");
    }

    @Test
    void diagramsWithoutBlockAttributesAreUntouchedAndUnknownAttributesArePreserved() {
        // S-PARSE-5, S-PARSE-6
        assertThat(adapter.extractProcessDefinitionKey(bpmn(service("updateDossierStatusTask", "")))).isEqualTo("testProcess");
        assertThat(adapter.extractProcessDefinitionKey(
                        bpmn(service("updateDossierStatusTask", "flowable:customThing=\"x\""))))
                .isEqualTo("testProcess");
    }

    @Test
    void delegateAllowlistRemainsTheGatekeeper() {
        // S-PARSE-7 : un attribut de brique ne rend pas un delegate inconnu acceptable
        assertRefused(service("evilBean", "flowable:targetDossierStatus=\"CLOTURE\""), "evilBean");
        assertRefused(service("evilBean", ""), "allowlist");
    }

    @Test
    void referencesToTenantResourcesAreExtractedForTheDeploymentCheck() {
        byte[] xml = bpmn(service("createChildDossierTask",
                "flowable:childDossierTypeCode=\"ENFANT\" flowable:childDossierLabel=\"Suite\""
                        + " flowable:workflowProcessKey=\"suite\" flowable:relationType=\"SUBSEQUENT\""));

        assertThat(adapter.extractBlockReferences(xml))
                .extracting(BlockParameterUsage::activityId, BlockParameterUsage::valueSource, BlockParameterUsage::value)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("act", "dossierTypes", "ENFANT"),
                        org.assertj.core.groups.Tuple.tuple("act", "workflowTypes", "suite"));
        assertThat(adapter.extractBlockReferences(bpmn(service("updateDossierStatusTask", "")))).isEmpty();
    }

    @Test
    void sampleXmlCoversEveryBindingOfTheCatalog() {
        // garde-fou du test de conformité lui-même
        assertThat(BuildingBlockCatalog.all().stream().flatMap(b -> b.parameters().stream())
                        .map(BlockParameter::binding).distinct().toList())
                .containsExactlyInAnyOrder(ParameterBinding.values());
        assertThat(BuildingBlockCatalog.all().stream().flatMap(b -> b.parameters().stream())
                        .map(BlockParameter::type).distinct().toList())
                .contains(ParameterType.SIGNERS, ParameterType.SOURCE_REF, ParameterType.ENUM_MULTI);
    }

    // ---- TODO §75 (LC2) : la formule est validée au déploiement (syntaxe et constructions permises) ------------------

    private static String expression(String formula, String formulaResult) {
        return service("businessExpressionTask", "flowable:formula=\"" + formula.replace("\"", "&quot;")
                + "\" flowable:formulaResult=\"" + formulaResult + "\"");
    }

    @Test
    void aValidFormulaIsAccepted() {
        assertThat(adapter.extractProcessDefinitionKey(
                        bpmn(expression("dossier.fields.montant > 5000 and dossier.fields.pays = &quot;FR&quot;", "eleve"))))
                .isEqualTo("testProcess");
    }

    @Test
    void aSyntaxErrorInTheFormulaIsRefusedAtDeploymentWithTheActivity() {
        assertRefused(expression("dossier.fields.montant >", "eleve"), "formula");
        assertRefused(expression("dossier.fields.montant >", "eleve"), "act");
    }

    @Test
    void forbiddenConstructsInTheFormulaAreRefusedAtDeployment() {
        assertRefused(expression("$eval('1')", "x1"), "$eval");
        assertRefused(expression("$random()", "x1"), "$random");
        assertRefused(expression("$now()", "x1"), "$now");
        assertRefused(expression("$match(a, /b+/)", "x1"), "regular expression");
    }

    @Test
    void theFormulaAndItsResultVariableAreBothRequired() {
        assertRefused(service("businessExpressionTask", "flowable:formula=\"1\""), "formulaResult");
        assertRefused(service("businessExpressionTask", "flowable:formulaResult=\"x1\""), "formula");
        assertRefused(service("businessExpressionTask", ""), "required");
    }

    @Test
    void aFormulaAttributeOnAnotherServiceTaskIsStillRefused() {
        assertRefused(service("updateDossierStatusTask", "flowable:formula=\"1\""), "formula");
        assertRefused("<serviceTask id=\"act\" flowable:type=\"dmn\" flowable:formula=\"1\"/>", "formula");
    }

    // ---- TODO §78 (D3b) : la brique « Enregistrer des valeurs dans le dossier » ------------------------------------------

    private static String mapping(String lines) {
        return service("fieldMappingTask", "flowable:fieldMapping=\"" + lines.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;").replace("\n", "&#10;") + "\"");
    }

    @Test
    void aValidFieldMappingIsAccepted() {
        assertThat(adapter.extractProcessDefinitionKey(bpmn(mapping(
                "score = connectorResult.json.score\nmontant = {\"amount\": $string(connectorResult.json.total), \"currency\": \"EUR\"}"))))
                .isEqualTo("testProcess");
    }

    @Test
    void anInvalidExpressionInTheMappingIsRefusedAtDeploymentNamingTheFieldAndTheActivity() {
        assertRefused(mapping("score = connectorResult.json.score >"), "score");
        assertRefused(mapping("score = connectorResult.json.score >"), "act");
        assertRefused(mapping("ok = 1\nbad = $eval('1')"), "$eval");
    }

    @Test
    void aMalformedMappingIsRefusedAtDeployment() {
        assertRefused(mapping("pas de signe egal"), "field = expression");
        assertRefused(mapping("1abc = 1"), "valid field name");
        assertRefused(mapping("a = 1\na = 2"), "twice");
        assertRefused(mapping("a == 1"), "field = expression");
        assertRefused(mapping("a ="), "no expression");
        assertRefused(service("fieldMappingTask", ""), "required");
        assertRefused(mapping(String.join("\n", java.util.stream.IntStream.rangeClosed(1, 21).mapToObj(i -> "f" + i + " = 1").toList())),
                "at most 20");
    }

    @Test
    void aFieldMappingAttributeOnAnotherServiceTaskIsRefused() {
        assertRefused(service("updateDossierStatusTask", "flowable:fieldMapping=\"a = 1\""), "fieldMapping");
    }

    @Test
    void thePlainFlowableExpressionAttributeRemainsAForbiddenSurface() {
        // La formule ne réintroduit pas de surface UEL : `flowable:expression` reste refusé (TODO §69).
        assertRefused("<serviceTask id=\"act\" flowable:expression=\"${x}\"/>", "expression");
    }
}
