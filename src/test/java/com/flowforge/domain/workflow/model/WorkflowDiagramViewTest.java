package com.flowforge.domain.workflow.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowDiagramViewTest {

    @Test
    void copiesIdListsImmutably() {
        WorkflowDiagramView view = new WorkflowDiagramView(
                "<xml/>", List.of("a1"), List.of("c1"), List.of("f1"));

        assertThat(view.bpmnXml()).isEqualTo("<xml/>");
        assertThat(view.activeIds()).containsExactly("a1");
        assertThatThrownBy(() -> view.activeIds().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
