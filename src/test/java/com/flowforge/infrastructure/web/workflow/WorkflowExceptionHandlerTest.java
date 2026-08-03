package com.flowforge.infrastructure.web.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.dossier.model.DossierWorkflowLinkNotFoundException;
import com.flowforge.domain.workflow.model.WorkflowTaskNotFoundException;
import com.flowforge.domain.workflow.model.WorkflowTypeConfigNotFoundException;
import org.junit.jupiter.api.Test;

class WorkflowExceptionHandlerTest {

    private final WorkflowExceptionHandler handler = new WorkflowExceptionHandler();

    @Test
    void handlesWorkflowTypeConfigNotFound_as404() {
        var problem = handler.handleWorkflowResourceNotFound(
                new WorkflowTypeConfigNotFoundException("Workflow type config not found: smokeProcess"));

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getTitle()).isEqualTo("Workflow resource not found");
    }

    @Test
    void handlesDossierWorkflowLinkNotFound_as404() {
        var problem = handler.handleWorkflowResourceNotFound(
                new DossierWorkflowLinkNotFoundException("Workflow link not found: abc"));

        assertThat(problem.getStatus()).isEqualTo(404);
    }

    @Test
    void handlesWorkflowTaskNotFound_as404() {
        var problem = handler.handleWorkflowResourceNotFound(
                new WorkflowTaskNotFoundException("Workflow task not found: task-1"));

        assertThat(problem.getStatus()).isEqualTo(404);
    }

    @Test
    void handleBadRequest_doesNotSniffMessageContent_alwaysReturns400() {
        // Un message métier contenant incidemment "not found" ne doit plus être requalifié en 404 :
        // seules les exceptions typées ci-dessus le sont désormais.
        var problem = handler.handleBadRequest(new IllegalArgumentException("resource not found upstream"));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Bad request");
    }
}
