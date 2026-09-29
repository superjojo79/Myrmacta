package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.signature.model.SignatureBlockDefinition;
import com.flowforge.domain.signature.model.SignatureMode;
import com.flowforge.domain.signature.model.SignerKind;
import com.flowforge.domain.workflow.AllowedBpmnDelegates;
import com.flowforge.domain.workflow.model.InvalidBpmnException;
import com.flowforge.domain.workflow.portal.model.PortalRecipientSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** TODO §72 — S-PARSE-1 à 5, S-PROG-5/6 : bloc « Demander une signature » (D-SIGNATURE-BPMN-MARKER). */
class FlowableBpmnParserAdapterSignatureTest {

    private final FlowableBpmnParserAdapter adapter = new FlowableBpmnParserAdapter();

    private static final String OK_ATTRS = "flowable:delegateExpression=\"${signatureRequestTask}\""
            + " flowable:signatureDocument=\"variable:generatedDocumentId\"";

    private static String signer(String attrs) {
        return "<flowable:signer " + attrs + "/>";
    }

    private static String task(String attrs, String... signers) {
        return "<serviceTask id=\"sign\" " + attrs + "><extensionElements>" + String.join("", signers)
                + "</extensionElements></serviceTask>";
    }

    @Test
    void allowlistAcceptsTheSignatureDelegateOnlyInItsExactForms() {
        // S-PARSE-1
        assertThat(AllowedBpmnDelegates.isAllowedDelegateExpression("${signatureRequestTask}")).isTrue();
        assertThat(AllowedBpmnDelegates.isAllowedDelegateExpression("signatureRequestTask")).isTrue();
        assertThat(AllowedBpmnDelegates.isAllowedDelegateExpression("${signatureRequestTask.x}")).isFalse();
        assertThat(AllowedBpmnDelegates.isAllowedDelegateExpression("${signatureRequestTask()}")).isFalse();
        assertThat(AllowedBpmnDelegates.isAllowedDelegateExpression("${signature Request}")).isFalse();
        assertThat(AllowedBpmnDelegates.isAllowedDelegateExpression("#{signatureRequestTask}")).isFalse();
    }

    @Test
    void validBlockIsAcceptedAndExtracted() {
        // S-PARSE-2
        byte[] xml = bpmn(task(
                OK_ATTRS + " flowable:signatureMode=\"SEQUENTIAL\" flowable:signatureDeadlineDays=\"14\"",
                signer("source=\"dossierField:emailClient\" kind=\"EXTERNAL\" required=\"true\" name=\"Client\""),
                signer("source=\"dossierField:gestionnaireId\" kind=\"INTERNAL\" required=\"false\"")));

        assertThat(adapter.extractProcessDefinitionKey(xml)).isEqualTo("testProcess");
        List<SignatureBlockDefinition> blocks = adapter.extractSignatureBlocks(xml);
        assertThat(blocks).singleElement().satisfies(b -> {
            assertThat(b.activityId()).isEqualTo("sign");
            assertThat(b.mode()).isEqualTo(SignatureMode.SEQUENTIAL);
            assertThat(b.deadlineDays()).isEqualTo(14);
            assertThat(b.document())
                    .isEqualTo(new PortalRecipientSource(PortalRecipientSource.Kind.PROCESS_VARIABLE, "generatedDocumentId"));
            assertThat(b.signers()).hasSize(2);
            assertThat(b.signers().get(0).kind()).isEqualTo(SignerKind.EXTERNAL);
            assertThat(b.signers().get(0).name()).isEqualTo("Client");
            assertThat(b.signers().get(1).required()).isFalse();
            assertThat(b.signers().get(1).name()).isEqualTo("gestionnaireId");
        });
    }

    @Test
    void modeDefaultsToParallelAndRequiredDefaultsToTrue() {
        byte[] xml = bpmn(task(OK_ATTRS, signer("source=\"variable:mail\" kind=\"EXTERNAL\"")));
        adapter.extractProcessDefinitionKey(xml);
        assertThat(adapter.extractSignatureBlocks(xml)).singleElement().satisfies(b -> {
            assertThat(b.mode()).isEqualTo(SignatureMode.PARALLEL);
            assertThat(b.deadlineDays()).isNull();
            assertThat(b.signers().get(0).required()).isTrue();
        });
    }

    @Test
    void blockInSubProcessIsExtracted() {
        byte[] xml = bpmn("<subProcess id=\"sub\"><startEvent id=\"s2\"/>"
                + task(OK_ATTRS, signer("source=\"variable:mail\" kind=\"EXTERNAL\""))
                + "<endEvent id=\"e2\"/><sequenceFlow sourceRef=\"s2\" targetRef=\"sign\"/>"
                + "<sequenceFlow sourceRef=\"sign\" targetRef=\"e2\"/></subProcess>", "sub");
        adapter.extractProcessDefinitionKey(xml);
        assertThat(adapter.extractSignatureBlocks(xml)).hasSize(1);
    }

    @Test
    void processWithoutSignatureBlockIsUnaffected() {
        // S-PARSE-4
        byte[] xml = bpmn("<serviceTask id=\"sign\" flowable:delegateExpression=\"${connectorServiceTask}\"/>");
        assertThat(adapter.extractProcessDefinitionKey(xml)).isEqualTo("testProcess");
        assertThat(adapter.extractSignatureBlocks(xml)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // document absent / mal formé
            "flowable:delegateExpression=\"${signatureRequestTask}\"",
            "flowable:delegateExpression=\"${signatureRequestTask}\" flowable:signatureDocument=\"doc\"",
            "flowable:delegateExpression=\"${signatureRequestTask}\" flowable:signatureDocument=\"variable:a.b\"",
            // mode invalide
            OK_ATTRS + " flowable:signatureMode=\"RANDOM\"",
            // échéance invalide ou hors bornes
            OK_ATTRS + " flowable:signatureDeadlineDays=\"0\"",
            OK_ATTRS + " flowable:signatureDeadlineDays=\"-2\"",
            OK_ATTRS + " flowable:signatureDeadlineDays=\"douze\"",
            OK_ATTRS + " flowable:signatureDeadlineDays=\"366\""
    })
    void invalidBlockAttributesAreRejected(String attrs) {
        byte[] xml = bpmn(task(attrs, signer("source=\"variable:mail\" kind=\"EXTERNAL\"")));
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("signature");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "source=\"mail\" kind=\"EXTERNAL\"",
            "source=\"variable:\" kind=\"EXTERNAL\"",
            "source=\"variable:a.b\" kind=\"EXTERNAL\"",
            "source=\"variable:mail\"",
            "source=\"variable:mail\" kind=\"GROUP\"",
            "source=\"variable:mail\" kind=\"EXTERNAL\" required=\"peut-etre\"",
            "source=\"variable:mail\" kind=\"EXTERNAL\" role=\"x\""
    })
    void invalidSignerElementIsRejected(String attrs) {
        byte[] xml = bpmn(task(OK_ATTRS, signer(attrs)));
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void blockNeedsSignersAtLeastOneRequiredAndDistinctSources() {
        // S-PROG-5, S-PROG-6
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(bpmn(task(OK_ATTRS))))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("at least one flowable:signer");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(bpmn(task(OK_ATTRS,
                        signer("source=\"variable:a\" kind=\"EXTERNAL\" required=\"false\"")))))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("required");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(bpmn(task(OK_ATTRS,
                        signer("source=\"variable:a\" kind=\"EXTERNAL\""),
                        signer("source=\"variable:a\" kind=\"INTERNAL\"")))))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("distinct");
    }

    @Test
    void signatureMarkupOutsideTheSignatureDelegateIsRejected() {
        byte[] wrongDelegate = bpmn(task(
                "flowable:delegateExpression=\"${connectorServiceTask}\" flowable:signatureDocument=\"variable:d\"",
                signer("source=\"variable:a\" kind=\"EXTERNAL\"")));
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(wrongDelegate))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("signature");
        byte[] onUserTask = bpmn("<userTask id=\"sign\" flowable:signatureDocument=\"variable:d\"/>");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(onUserTask))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("signature");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "flowable:delegateExpression=\"${signatureRequestTask}\" flowable:signatureDocument=\"variable:${x}\"",
            "flowable:delegateExpression=\"${signatureRequestTask}\" flowable:signatureDocument=\"variable:d\""
                    + " flowable:signatureMode=\"${mode}\"",
            "flowable:delegateExpression=\"${signatureRequestTask}\" flowable:signatureDocument=\"variable:d\""
                    + " flowable:signatureDeadlineDays=\"#{jours}\""
    })
    void expressionsInBlockAttributesAreRejected(String attrs) {
        // S-PARSE-3 / S-PARSE-5
        byte[] xml = bpmn(task(attrs, signer("source=\"variable:mail\" kind=\"EXTERNAL\"")));
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml)).isInstanceOf(InvalidBpmnException.class);
    }

    @Test
    void expressionsInSignerAttributesAreRejected() {
        byte[] xml = bpmn(task(OK_ATTRS,
                signer("source=\"variable:mail\" kind=\"EXTERNAL\" name=\"${beans}\"")));
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml)).isInstanceOf(InvalidBpmnException.class);
    }

    private static byte[] bpmn(String element) {
        return bpmn(element, "sign");
    }

    private static byte[] bpmn(String element, String elementId) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://flowforge/test">
                  <process id="testProcess" isExecutable="true">
                    <startEvent id="start"/>
                    %s
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="%s"/>
                    <sequenceFlow sourceRef="%s" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(element, elementId, elementId).getBytes(StandardCharsets.UTF_8);
    }
}
