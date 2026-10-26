package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FlowableBpmnParserAdapterTest {

    private static final String VALID_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         targetNamespace="http://myrmacta/test">
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
                             targetNamespace="http://myrmacta/test">
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
                             targetNamespace="http://myrmacta/test">
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
                             targetNamespace="http://myrmacta/test">
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
                             targetNamespace="http://myrmacta/test">
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
                             targetNamespace="http://myrmacta/test">
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
                             targetNamespace="http://myrmacta/test">
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

    // --- TODO §61 (introduction-dmn), D1 : serviceTask flowable:type="dmn" ---

    @Test
    void extractProcessDefinitionKey_dmnServiceTaskWithLiteralField_accepted() {
        String xml = dmnServiceTaskBpmn(
                "<flowable:field name=\"decisionTableReferenceKey\"><flowable:string>eligibilite</flowable:string></flowable:field>");
        assertThat(adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("testProcess");
    }

    @Test
    void extractReferencedDecisionKeys_dmnServiceTask_returnsTheKey() {
        String xml = dmnServiceTaskBpmn(
                "<flowable:field name=\"decisionTableReferenceKey\"><flowable:string>eligibilite</flowable:string></flowable:field>");
        assertThat(adapter.extractReferencedDecisionKeys(xml.getBytes(StandardCharsets.UTF_8)))
                .containsExactly("eligibilite");
    }

    @Test
    void extractReferencedDecisionKeys_noDmnServiceTask_returnsEmpty() {
        assertThat(adapter.extractReferencedDecisionKeys(VALID_BPMN.getBytes(StandardCharsets.UTF_8)))
                .isEmpty();
    }

    @Test
    void extractReferencedDecisionKeys_dmnServiceTaskNestedInSubProcess_returnsTheKey() {
        // Phase 7 gap : collectDecisionKeys() recurses into SubProcess.getFlowElements() -- never
        // exercised by a nested fixture before this review.
        String xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://myrmacta/test">
                  <process id="testProcess" isExecutable="true">
                    <startEvent id="start"/>
                    <subProcess id="sub">
                      <startEvent id="subStart"/>
                      <serviceTask id="svc" flowable:type="dmn">
                        <extensionElements>
                          <flowable:field name="decisionTableReferenceKey">
                            <flowable:string>eligibilite</flowable:string>
                          </flowable:field>
                        </extensionElements>
                      </serviceTask>
                      <endEvent id="subEnd"/>
                      <sequenceFlow sourceRef="subStart" targetRef="svc"/>
                      <sequenceFlow sourceRef="svc" targetRef="subEnd"/>
                    </subProcess>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="sub"/>
                    <sequenceFlow sourceRef="sub" targetRef="end"/>
                  </process>
                </definitions>
                """;
        assertThat(adapter.extractReferencedDecisionKeys(xml.getBytes(StandardCharsets.UTF_8)))
                .containsExactly("eligibilite");
    }

    @Test
    void extractProcessDefinitionKey_dmnServiceTaskWithExpressionField_rejected() {
        String xml = dmnServiceTaskBpmn(
                "<flowable:field name=\"decisionTableReferenceKey\"><flowable:expression>${connectorServiceTask}</flowable:expression></flowable:field>");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("literal");
    }

    @Test
    void extractProcessDefinitionKey_dmnServiceTaskWithWrongFieldName_rejected() {
        String xml = dmnServiceTaskBpmn(
                "<flowable:field name=\"somethingElse\"><flowable:string>eligibilite</flowable:string></flowable:field>");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("decisionTableReferenceKey");
    }

    @Test
    void extractProcessDefinitionKey_dmnServiceTaskWithNoField_rejected() {
        String xml = dmnServiceTaskBpmn("");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("exactly one field extension");
    }

    @Test
    void extractProcessDefinitionKey_dmnServiceTaskWithTwoFields_rejected() {
        String xml = dmnServiceTaskBpmn(
                """
                <flowable:field name="decisionTableReferenceKey"><flowable:string>eligibilite</flowable:string></flowable:field>
                <flowable:field name="extra"><flowable:string>x</flowable:string></flowable:field>
                """);
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("exactly one field extension");
    }

    @Test
    void extractProcessDefinitionKey_dmnServiceTaskWithElExpressionInLiteralValue_rejected() {
        // Same lesson as H1-bis: a "literal" that itself contains an EL marker is not safe just
        // because it arrived via flowable:string rather than flowable:expression.
        String xml = dmnServiceTaskBpmn(
                "<flowable:field name=\"decisionTableReferenceKey\"><flowable:string>${connectorServiceTask}</flowable:string></flowable:field>");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class);
    }

    @Test
    void extractProcessDefinitionKey_otherBuiltinServiceTaskType_stillRejected() {
        // Non-regression: the new type="dmn" exception must not widen into "any built-in type".
        String xml = serviceTaskBpmn("flowable:type=\"mail\"");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("built-in serviceTask type");
    }

    private static String dmnServiceTaskBpmn(String fieldExtensionsXml) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://myrmacta/test">
                  <process id="testProcess" isExecutable="true">
                    <startEvent id="start"/>
                    <serviceTask id="svc" flowable:type="dmn">
                      <extensionElements>
                        %s
                      </extensionElements>
                    </serviceTask>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="svc"/>
                    <sequenceFlow sourceRef="svc" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(fieldExtensionsXml);
    }
}
