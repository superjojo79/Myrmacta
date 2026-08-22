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
}
