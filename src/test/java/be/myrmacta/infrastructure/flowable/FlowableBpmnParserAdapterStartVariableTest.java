package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import be.myrmacta.domain.workflow.model.StartVariableDeclaration;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** TODO §111 (issue #187, D-START-DECLARED). */
class FlowableBpmnParserAdapterStartVariableTest {

    private final FlowableBpmnParserAdapter adapter = new FlowableBpmnParserAdapter();

    @Test
    void processStartVariablesAreAcceptedInOrder() {
        byte[] xml = process("""
                <extensionElements>
                  <flowable:startVariable name="motif" label="Motif de la demande" required="true"/>
                  <flowable:startVariable name="note" label="Note" required="false"/>
                </extensionElements>
                """);

        assertThat(adapter.extractProcessDefinitionKey(xml)).isEqualTo("declaredStart");
        assertThat(adapter.extractStartVariables(xml, "declaredStart")).containsExactly(
                new StartVariableDeclaration("motif", "Motif de la demande", true),
                new StartVariableDeclaration("note", "Note", false));
    }

    @Test
    void aProcessWithoutDeclarationsExtractsNothing() {
        byte[] xml = process("");

        assertThat(adapter.extractProcessDefinitionKey(xml)).isEqualTo("declaredStart");
        assertThat(adapter.extractStartVariables(xml, "declaredStart")).isEmpty();
    }

    @Test
    void declarationsAreThoseOfTheDeployedProcessNotTheFirstPool() {
        byte[] xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://myrmacta/test">
                  <collaboration id="collab">
                    <participant id="poolSilent" processRef="silent"/>
                  </collaboration>
                  <process id="silent" isExecutable="false">
                    <startEvent id="s1"/>
                    <endEvent id="e1"/>
                    <sequenceFlow id="f1" sourceRef="s1" targetRef="e1"/>
                  </process>
                  <process id="realProcess" isExecutable="true">
                    <extensionElements>
                      <flowable:startVariable name="motif" label="Motif" required="true"/>
                    </extensionElements>
                    <startEvent id="start"/>
                    <endEvent id="end"/>
                    <sequenceFlow id="toEnd" sourceRef="start" targetRef="end"/>
                  </process>
                </definitions>
                """.getBytes(StandardCharsets.UTF_8);

        assertThat(adapter.extractStartVariables(xml, "realProcess")).containsExactly(
                new StartVariableDeclaration("motif", "Motif", true));
    }

    @Test
    void aStartVariableOnALaneAParticipantOrAnArtifactIsRejected() {
        byte[] lane = process("""
                <laneSet id="lanes">
                  <lane id="clerk">
                    <extensionElements>
                      <flowable:startVariable name="motif" label="Motif" required="true"/>
                    </extensionElements>
                    <flowNodeRef>start</flowNodeRef>
                    <flowNodeRef>end</flowNodeRef>
                  </lane>
                </laneSet>
                """);
        byte[] participant = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://myrmacta/test">
                  <collaboration id="collab">
                    <participant id="pool1" processRef="declaredStart">
                      <extensionElements>
                        <flowable:startVariable name="motif" label="Motif" required="true"/>
                      </extensionElements>
                    </participant>
                  </collaboration>
                  <process id="declaredStart" isExecutable="true">
                    <startEvent id="start"/>
                    <endEvent id="end"/>
                    <sequenceFlow id="toEnd" sourceRef="start" targetRef="end"/>
                  </process>
                </definitions>
                """.getBytes(StandardCharsets.UTF_8);
        byte[] annotation = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://myrmacta/test">
                  <process id="declaredStart" isExecutable="true">
                    <startEvent id="start"/>
                    <endEvent id="end"/>
                    <sequenceFlow id="toEnd" sourceRef="start" targetRef="end"/>
                    <textAnnotation id="hint">
                      <extensionElements>
                        <flowable:startVariable name="motif" label="Motif" required="true"/>
                      </extensionElements>
                      <text>note</text>
                    </textAnnotation>
                  </process>
                </definitions>
                """.getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(lane))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("only allowed on the process");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(participant))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("only allowed on the process");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(annotation))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("only allowed on the process");

        byte[] nestedAnnotation = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://myrmacta/test">
                  <process id="declaredStart" isExecutable="true">
                    <startEvent id="start"/>
                    <subProcess id="inner">
                      <startEvent id="innerStart"/>
                      <endEvent id="innerEnd"/>
                      <sequenceFlow id="innerFlow" sourceRef="innerStart" targetRef="innerEnd"/>
                      <textAnnotation id="innerHint">
                        <extensionElements>
                          <flowable:startVariable name="motif" label="Motif" required="true"/>
                        </extensionElements>
                        <text>note</text>
                      </textAnnotation>
                    </subProcess>
                    <endEvent id="end"/>
                    <sequenceFlow id="toInner" sourceRef="start" targetRef="inner"/>
                    <sequenceFlow id="toEnd" sourceRef="inner" targetRef="end"/>
                  </process>
                </definitions>
                """.getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(nestedAnnotation))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("only allowed on the process");
    }

    @Test
    void aStartVariableOnAnActivityIsRejected() {
        byte[] xml = process("""
                <userTask id="wait">
                  <extensionElements>
                    <flowable:startVariable name="motif" label="Motif" required="true"/>
                  </extensionElements>
                </userTask>
                """);

        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("only allowed on the process");
    }

    @Test
    void aReservedNameIsRejectedAtParseTime() {
        byte[] xml = process("""
                <extensionElements>
                  <flowable:startVariable name="dossierId" label="Dossier" required="true"/>
                </extensionElements>
                """);

        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("reserved")
                .hasMessageContaining("dossierId");
    }

    private static byte[] process(String body) {
        String flow = body.contains("<userTask")
                ? """
                <sequenceFlow id="toWait" sourceRef="start" targetRef="wait"/>
                <sequenceFlow id="toEnd" sourceRef="wait" targetRef="end"/>
                """
                : "<sequenceFlow id=\"toEnd\" sourceRef=\"start\" targetRef=\"end\"/>";
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://myrmacta/test">
                  <process id="declaredStart" isExecutable="true">
                    %s
                    <startEvent id="start"/>
                    %s
                    <endEvent id="end"/>
                    %s
                  </process>
                </definitions>
                """.formatted(body, body.contains("<userTask") ? "" : "", flow).getBytes(StandardCharsets.UTF_8);
    }
}
