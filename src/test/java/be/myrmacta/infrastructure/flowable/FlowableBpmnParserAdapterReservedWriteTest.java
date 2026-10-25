package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Issue #203 (TODO §99, D-BPMN-RESERVED-WRITE) — le BPMN déployé ne peut pas recopier une valeur dans une variable
 * réservée. Preuve moteur des deux scénarios de l'issue : {@link ReservedVariableWriteIT}.
 */
class FlowableBpmnParserAdapterReservedWriteTest {

    private static final String ATTACH = "flowable:delegateExpression=\"${attachGeneratedDocumentTask}\"";

    private final FlowableBpmnParserAdapter adapter = new FlowableBpmnParserAdapter();

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            // Scénario A : la boucle crée une variable locale lue en priorité par le délégué.
            "<serviceTask id=\"wait\" " + ATTACH + "><multiInstanceLoopCharacteristics isSequential=\"true\" "
                    + "flowable:collection=\"refs\" flowable:elementVariable=\"generatedDocumentId\"/></serviceTask>",
            "<serviceTask id=\"wait\" " + ATTACH + "><multiInstanceLoopCharacteristics isSequential=\"true\" "
                    + "flowable:collection=\"refs\" flowable:elementIndexVariable=\"dossierId\"/></serviceTask>",
            // Forme standard de l'elementVariable.
            "<serviceTask id=\"wait\" " + ATTACH + "><multiInstanceLoopCharacteristics isSequential=\"true\">"
                    + "<loopDataInputRef>refs</loopDataInputRef><inputDataItem name=\"generatedDocumentId\"/>"
                    + "</multiInstanceLoopCharacteristics></serviceTask>",
            // Espaces de bord : même nom pour la règle.
            "<serviceTask id=\"wait\" " + ATTACH + "><multiInstanceLoopCharacteristics isSequential=\"true\" "
                    + "flowable:collection=\"refs\" flowable:elementVariable=\" generatedDocumentId \"/></serviceTask>",
            // Scénario B : mappings d'un sous-processus appelé.
            "<callActivity id=\"wait\" calledElement=\"child\"><extensionElements>"
                    + "<flowable:in source=\"docRef\" target=\"generatedDocumentId\"/></extensionElements></callActivity>",
            "<callActivity id=\"wait\" calledElement=\"child\"><extensionElements>"
                    + "<flowable:in source=\"otherDossier\" target=\"dossierId\"/></extensionElements></callActivity>",
            "<callActivity id=\"wait\" calledElement=\"child\"><extensionElements>"
                    + "<flowable:out source=\"childValue\" target=\"connectorResult\"/></extensionElements></callActivity>",
            // Une valeur littérale n'est pas une recopie à l'identique.
            "<callActivity id=\"wait\" calledElement=\"child\"><extensionElements>"
                    + "<flowable:in sourceExpression=\"dossierId\" target=\"dossierId\"/></extensionElements></callActivity>",
            "<callActivity id=\"wait\" calledElement=\"child\"><extensionElements>"
                    + "<flowable:in source=\"docRef\" targetExpression=\"dossierId\"/></extensionElements></callActivity>",
            "<callActivity id=\"wait\" calledElement=\"child\"><extensionElements>"
                    + "<flowable:in source=\"dossierId\" target=\"dossierId\" targetExpression=\"dossierId\"/>"
                    + "</extensionElements></callActivity>",
            // Autres écritures portées par un attribut.
            "<callActivity id=\"wait\" calledElement=\"child\" flowable:idVariableName=\"dossierWorkflowLinkId\"/>",
            "<userTask id=\"wait\" flowable:taskIdVariableName=\"dossierId\"/>",
            "<userTask id=\"wait\" flowable:taskCompleterVariableName=\"tenantId\"/>",
            "<userTask id=\"wait\"><extensionElements><flowable:formProperty id=\"dossierWorkflowLinkId\"/>"
                    + "</extensionElements></userTask>",
            "<userTask id=\"wait\"><extensionElements><flowable:formProperty id=\"answer\" variable=\"dossierId\"/>"
                    + "</extensionElements></userTask>",
            "<userTask id=\"wait\"><extensionElements><flowable:eventOutParameter source=\"payload\" "
                    + "target=\"dossierId\"/></extensionElements></userTask>",
            "<userTask id=\"wait\"><multiInstanceLoopCharacteristics isSequential=\"true\" flowable:collection=\"refs\" "
                    + "flowable:elementVariable=\"ref\"><extensionElements><flowable:variableAggregation "
                    + "target=\"dossierFields\"><variable source=\"ref\" target=\"ref\"/></flowable:variableAggregation>"
                    + "</extensionElements></multiInstanceLoopCharacteristics></userTask>",
            // Variable posée à l'entrée d'un sous-processus.
            "<subProcess id=\"wait\"><dataObject id=\"forged\" name=\"generatedDocumentId\" itemSubjectRef=\"xsd:string\">"
                    + "<extensionElements><flowable:value>other-ref</flowable:value></extensionElements></dataObject>"
                    + "<startEvent id=\"subStart\"/><endEvent id=\"subEnd\"/>"
                    + "<sequenceFlow id=\"subFlow\" sourceRef=\"subStart\" targetRef=\"subEnd\"/></subProcess>",
    })
    void aReservedVariableWrite_isRejected(String element) {
        assertRejected(process(element));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "<startEvent id=\"start\" flowable:initiator=\"dossierId\"/>",
            "<dataObject id=\"forged\" name=\"dossierId\" itemSubjectRef=\"xsd:string\"/><startEvent id=\"start\"/>",
            // Préfixe activiti: accepté par le convertisseur Flowable.
            "<startEvent id=\"start\" activiti:initiator=\"tenantId\"/>",
    })
    void aReservedVariableWriteAtStart_isRejected(String start) {
        assertRejected(wrap(start + "<userTask id=\"wait\"/><endEvent id=\"end\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"wait\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"wait\" targetRef=\"end\"/>"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "<boundaryEvent id=\"onError\" attachedToRef=\"wait\"><errorEventDefinition "
                    + "flowable:errorVariableName=\"connectorResult\"/></boundaryEvent>",
    })
    void aReservedErrorVariable_isRejected(String boundary) {
        assertRejected(wrap("<startEvent id=\"start\"/><userTask id=\"wait\"/><endEvent id=\"end\"/>" + boundary
                + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"wait\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"wait\" targetRef=\"end\"/>"
                + "<sequenceFlow id=\"f3\" sourceRef=\"onError\" targetRef=\"end\"/>"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            // Recopie à l'identique : le sous-processus reçoit le même dossier.
            "<callActivity id=\"wait\" calledElement=\"child\"><extensionElements>"
                    + "<flowable:in source=\"dossierId\" target=\"dossierId\"/>"
                    + "<flowable:in source=\"tenantId\" target=\"tenantId\"/>"
                    + "<flowable:out source=\"generatedDocumentId\" target=\"generatedDocumentId\"/>"
                    + "</extensionElements></callActivity>",
            "<callActivity id=\"wait\" calledElement=\"child\"><extensionElements>"
                    + "<flowable:in variables=\"all\"/><flowable:out variables=\"all\"/></extensionElements></callActivity>",
            // Lire une variable réservée sous un autre nom reste permis.
            "<callActivity id=\"wait\" calledElement=\"child\"><extensionElements>"
                    + "<flowable:in source=\"dossierId\" target=\"parentDossierId\"/></extensionElements></callActivity>",
            "<serviceTask id=\"wait\" " + ATTACH + "><multiInstanceLoopCharacteristics isSequential=\"true\" "
                    + "flowable:collection=\"dossierFields\" flowable:elementVariable=\"field\"/></serviceTask>",
            "<userTask id=\"wait\" flowable:taskIdVariableName=\"reviewTaskId\"/>",
    })
    void aCopyThatKeepsReservedValues_isAccepted(String element) {
        assertThat(adapter.extractProcessDefinitionKey(process(element).getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("testProcess");
    }

    private void assertRejected(String xml) {
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("BPMN cannot write the reserved variable");
    }

    private static String process(String element) {
        return wrap("<startEvent id=\"start\"/>" + element + "<endEvent id=\"end\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"wait\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"wait\" targetRef=\"end\"/>");
    }

    private static String wrap(String body) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             xmlns:activiti="http://activiti.org/bpmn"
                             xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                             targetNamespace="http://myrmacta/test">
                  <process id="testProcess" isExecutable="true">
                    %s
                  </process>
                </definitions>
                """.formatted(body);
    }
}
