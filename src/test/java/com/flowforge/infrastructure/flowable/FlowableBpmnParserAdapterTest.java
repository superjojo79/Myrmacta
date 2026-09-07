package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.workflow.model.InvalidBpmnException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FlowableBpmnParserAdapterTest {

    private static final String VALID_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         targetNamespace="http://flowforge/test">
              <process id="testProcess" isExecutable="true">
                <startEvent id="start"/>
                <endEvent id="end"/>
                <sequenceFlow id="flow1" sourceRef="start" targetRef="end"/>
              </process>
            </definitions>
            """;

    private static final String MALFORMED_XML = "<not-even-xml";

    private static final String NON_BPMN_XML = "<root><child/></root>";

    private final FlowableBpmnParserAdapter adapter = new FlowableBpmnParserAdapter();

    @Test
    void extractProcessDefinitionKey_validBpmn_returnsProcessId() {
        String key = adapter.extractProcessDefinitionKey(VALID_BPMN.getBytes(StandardCharsets.UTF_8));

        assertThat(key).isEqualTo("testProcess");
    }

    @Test
    void extractProcessDefinitionKey_malformedXml_throwsInvalidBpmnException() {
        assertThatThrownBy(() ->
                        adapter.extractProcessDefinitionKey(MALFORMED_XML.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class);
    }

    @Test
    void extractProcessDefinitionKey_wellFormedButNotBpmn_throwsInvalidBpmnException() {
        assertThatThrownBy(() ->
                        adapter.extractProcessDefinitionKey(NON_BPMN_XML.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class);
    }

    @Test
    void extractProcessDefinitionKey_allowedDelegateExpression_returnsProcessId() {
        String xml = serviceTaskBpmn("flowable:delegateExpression=\"${connectorServiceTask}\"");
        assertThat(adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("testProcess");
    }

    @Test
    void extractProcessDefinitionKey_scriptTask_rejected() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://flowforge/test">
                  <process id="testProcess" isExecutable="true">
                    <startEvent id="start"/>
                    <scriptTask id="evil" scriptFormat="juel">
                      <script>${true}</script>
                    </scriptTask>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="evil"/>
                    <sequenceFlow sourceRef="evil" targetRef="end"/>
                  </process>
                </definitions>
                """;
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("scriptTask");
    }

    @Test
    void extractProcessDefinitionKey_classImplementation_rejected() {
        String xml = serviceTaskBpmn("flowable:class=\"java.lang.Runtime\"");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("implementation type");
    }

    @Test
    void extractProcessDefinitionKey_expressionImplementation_rejected() {
        String xml = serviceTaskBpmn("flowable:expression=\"${execution.getProcessEngine()}\"");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("implementation type");
    }

    @Test
    void extractProcessDefinitionKey_unknownDelegate_rejected() {
        String xml = serviceTaskBpmn("flowable:delegateExpression=\"${java.lang.Runtime}\"");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("allowlist");
    }

    // --- TODO §57 revue adversariale PR #113 (H1 gap, confirmé par test réel --
    // FlowableBpmnAllowlistGapTest) : assignee/candidateUsers/candidateGroups,
    // conditionExpression et flowable:field ne sont pas validés par la version initiale de
    // l'allowlist. ---

    @Test
    void extractProcessDefinitionKey_assigneeBeanExpression_rejected() {
        String xml = userTaskBpmn("flowable:assignee=\"${connectorServiceTask}\"");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("assignee");
    }

    @Test
    void extractProcessDefinitionKey_assigneeMethodCallExpression_rejected() {
        String xml = userTaskBpmn("flowable:assignee=\"${connectorServiceTask.toString()}\"");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("assignee");
    }

    /**
     * A bare single identifier is exactly as ambiguous as {@code ${connectorServiceTask}} above --
     * UEL falls back to bean-by-name resolution whenever no process variable of that name exists,
     * so {@code ${initiator}} is rejected the same as any other bare expression, not just method
     * calls. See {@code extractProcessDefinitionKey_assigneeBeanExpression_rejected}.
     */
    @Test
    void extractProcessDefinitionKey_assigneeSimpleVariableExpression_rejected() {
        String xml = userTaskBpmn("flowable:assignee=\"${initiator}\"");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("assignee");
    }

    @Test
    void extractProcessDefinitionKey_assigneeLiteral_isAllowed() {
        String xml = userTaskBpmn("flowable:assignee=\"kermit\"");
        assertThat(adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("testProcess");
    }

    @Test
    void extractProcessDefinitionKey_candidateUsersBeanExpression_rejected() {
        String xml = userTaskBpmn("flowable:candidateUsers=\"${connectorServiceTask}\"");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("candidateUsers");
    }

    @Test
    void extractProcessDefinitionKey_candidateGroupsBeanExpression_rejected() {
        String xml = userTaskBpmn("flowable:candidateGroups=\"${connectorServiceTask}\"");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("candidateGroups");
    }

    @Test
    void extractProcessDefinitionKey_conditionExpressionWithMethodCall_rejected() {
        String xml = conditionBpmn("${connectorServiceTask.toString() != null}");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("conditionExpression");
    }

    @Test
    void extractProcessDefinitionKey_conditionExpressionComparison_isAllowed() {
        String xml = conditionBpmn("${amount > 1000}");
        assertThat(adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("testProcess");
    }

    @Test
    void extractProcessDefinitionKey_conditionExpressionStringEqualityLikeSmokeSample_isAllowed() {
        // Mirrors the only real conditionExpression usage shipped with this repository
        // (user-question-smoke.bpmn20.xml): ${decision == 'approve'}.
        String xml = conditionBpmn("${decision == 'approve'}");
        assertThat(adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("testProcess");
    }

    /**
     * Same ambiguity as bare {@code assignee} expressions: with no operator at all, {@code
     * ${connectorServiceTask}} is indistinguishable from a legitimate bare boolean process
     * variable reference, and UEL falls back to resolving the Spring bean of that name.
     */
    @Test
    void extractProcessDefinitionKey_conditionExpressionBareBeanReference_rejected() {
        String xml = conditionBpmn("${connectorServiceTask}");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("conditionExpression");
    }

    @Test
    void extractProcessDefinitionKey_conditionExpressionPropertyAccessWithoutParens_rejected() {
        // UEL resolves a getter through plain property access -- no literal "(" required -- so a
        // dot alone is enough to invoke a method; banning it independently of parens closes that.
        String xml = conditionBpmn("${connectorServiceTask.class != null}");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("conditionExpression");
    }

    @Test
    void extractProcessDefinitionKey_serviceTaskFieldExtension_rejected() {
        String xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://flowforge/test">
                  <process id="testProcess" isExecutable="true">
                    <startEvent id="start"/>
                    <serviceTask id="svc" flowable:delegateExpression="${connectorServiceTask}">
                      <extensionElements>
                        <flowable:field name="x">
                          <flowable:expression>${connectorServiceTask}</flowable:expression>
                        </flowable:field>
                      </extensionElements>
                    </serviceTask>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="svc"/>
                    <sequenceFlow sourceRef="svc" targetRef="end"/>
                  </process>
                </definitions>
                """;
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("field extensions");
    }

    @Test
    void extractProcessDefinitionKey_businessRuleTask_rejected() {
        String xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             targetNamespace="http://flowforge/test">
                  <process id="testProcess" isExecutable="true">
                    <startEvent id="start"/>
                    <businessRuleTask id="dmn"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="dmn"/>
                    <sequenceFlow sourceRef="dmn" targetRef="end"/>
                  </process>
                </definitions>
                """;
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("businessRuleTask");
    }

    private static String userTaskBpmn(String userTaskAttr) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://flowforge/test">
                  <process id="testProcess" isExecutable="true">
                    <startEvent id="start"/>
                    <userTask id="wait" %s/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="wait"/>
                    <sequenceFlow sourceRef="wait" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(userTaskAttr);
    }

    private static String conditionBpmn(String condition) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             targetNamespace="http://flowforge/test">
                  <process id="testProcess" isExecutable="true">
                    <startEvent id="start"/>
                    <exclusiveGateway id="gw"/>
                    <userTask id="a"/>
                    <userTask id="b"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="gw"/>
                    <sequenceFlow id="toA" sourceRef="gw" targetRef="a">
                      <conditionExpression xsi:type="tFormalExpression" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">%s</conditionExpression>
                    </sequenceFlow>
                    <sequenceFlow sourceRef="gw" targetRef="b"/>
                    <sequenceFlow sourceRef="a" targetRef="end"/>
                    <sequenceFlow sourceRef="b" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(condition);
    }

    private static String serviceTaskBpmn(String serviceTaskAttr) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://flowforge/test">
                  <process id="testProcess" isExecutable="true">
                    <startEvent id="start"/>
                    <serviceTask id="svc" %s/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="svc"/>
                    <sequenceFlow sourceRef="svc" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(serviceTaskAttr);
    }
}
