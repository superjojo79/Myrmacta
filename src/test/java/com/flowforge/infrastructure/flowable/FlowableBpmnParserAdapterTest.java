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
