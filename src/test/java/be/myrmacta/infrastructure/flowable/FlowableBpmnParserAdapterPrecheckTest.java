package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.precheck.model.PrecheckBlock;
import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TODO §106 (lot B, D-PRECHECK-BLOCK, D-PRECHECK-SPEC) — la brique « Pré-vérification IA » au déploiement. */
class FlowableBpmnParserAdapterPrecheckTest {

    private final FlowableBpmnParserAdapter adapter = new FlowableBpmnParserAdapter();

    private static byte[] bpmn(String elements) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://myrmacta/test">
                  <process id="testProcess" isExecutable="true">
                    <startEvent id="start"/>
                    %s
                  </process>
                </definitions>
                """.formatted(elements).getBytes(StandardCharsets.UTF_8);
    }

    private static String precheck(String id, boolean async, String attributes) {
        return "<serviceTask id=\"" + id + "\" flowable:delegateExpression=\"${aiPrecheckTask}\""
                + (async ? " flowable:async=\"true\"" : "") + " " + attributes + "/>";
    }

    private void assertRefused(String attributes, String messagePart) {
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(bpmn(precheck("check", true, attributes))))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining(messagePart);
    }

    @Test
    void anAsynchronousBlockIsAcceptedAndExtracted() {
        byte[] xml = bpmn(precheck("check", true, "flowable:precheckPieces=\"preuve : obligatoire, lire\" "
                + "flowable:precheckQuestions=\"adresse = Même adresse ?&#10;signe = Signé ?\" "
                + "flowable:precheckResult=\"precheck\"")
                + "<subProcess id=\"sub\"><startEvent id=\"s2\"/>"
                + precheck("inner", true, "flowable:precheckRules=\"variable:doublons &gt; 0 =&gt; INCOHERENCE : Doublon.\"")
                + "</subProcess>");

        assertThat(adapter.extractProcessDefinitionKey(xml)).isEqualTo("testProcess");
        List<PrecheckBlock> blocks = adapter.extractPrecheckBlocks(xml);
        assertThat(blocks).extracting(PrecheckBlock::activityId).containsExactly("check", "inner");
        assertThat(blocks.getFirst().spec().questions()).hasSize(2);
        assertThat(blocks.getFirst().spec().resultVariable()).isEqualTo("precheck");
        assertThat(blocks.get(1).spec().rules()).hasSize(1);
    }

    @Test
    void aProcessWithoutTheBlockHasNone() {
        assertThat(adapter.extractPrecheckBlocks(bpmn("<endEvent id=\"end\"/>"))).isEmpty();
    }

    @Test
    void aSynchronousBlockIsRefused() {
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(
                bpmn(precheck("check", false, "flowable:precheckPieces=\"preuve : lire\""))))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("flowable:async=\"true\"");
    }

    @Test
    void anEmptySpecificationIsRefused() {
        assertRefused("flowable:precheckFields=\"commune\"", "at least one piece, question or rule");
    }

    @Test
    void eachAttributeGrammarIsChecked() {
        assertRefused("flowable:precheckPieces=\"preuve : ecrire\"", "unknown flag");
        assertRefused("flowable:precheckPieces=\"preuve : lire\" flowable:precheckFields=\"a b\"", "invalid field name");
        assertRefused("flowable:precheckQuestions=\"pas de signe\"", "expected 'key = question'");
        assertRefused("flowable:precheckRules=\"variable:x contient 2 =&gt; MANQUANT : m\"", "unknown condition");
        assertRefused("flowable:precheckRules=\"variable:dossierId est vide =&gt; MANQUANT : m\"", "reserved");
        assertRefused("flowable:precheckPieces=\"preuve : lire\" flowable:precheckResult=\"dossierId\"", "dossierId");
    }

    @Test
    void attributesAreLiteral() {
        assertRefused("flowable:precheckPieces=\"${pieces}\"", "literal");
        assertRefused("flowable:precheckPieces=\"preuve : lire\" flowable:precheckResult=\"#{x}\"", "literal");
    }

    @Test
    void precheckAttributesBelongToTheBlockOnly() {
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(bpmn("<serviceTask id=\"act\" "
                + "flowable:delegateExpression=\"${updateDossierStatusTask}\" flowable:targetDossierStatus=\"CLOTURE\" "
                + "flowable:precheckPieces=\"preuve : lire\"/>")))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("precheckPieces");
    }
}
