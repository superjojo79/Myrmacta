package be.myrmacta.infrastructure.web.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.DossierWorkflowLinkNotFoundException;
import be.myrmacta.domain.notification.model.NotificationValidationException;
import be.myrmacta.domain.workflow.model.WorkflowProcessInstanceMissingException;
import be.myrmacta.domain.workflow.model.WorkflowTaskNotFoundException;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfigNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;

class WorkflowExceptionHandlerTest {

    private final WorkflowExceptionHandler handler = new WorkflowExceptionHandler();

    /**
     * Revue adversariale TODO §93 (M1) : une brique « Notifier » synchrone qui échoue (destinataire lu dans le dossier vide ou
     * mal formé) remonte dans la complétion de tâche ou le démarrage : 422 explicite, pas une 500. Le message nomme la source,
     * jamais l'adresse, pour que l'éditeur corrige le champ.
     */
    @Test
    void handlesAFailedNotificationStep_as422WithTheSourceNamed() {
        var problem = handler.handleWorkflowStepFailed(new NotificationValidationException(
                "notificationTo source dossierField:email resolved to an empty value"));

        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(problem.getTitle()).isEqualTo("Workflow step failed");
        assertThat(problem.getDetail()).contains("dossierField:email");
    }

    @Test
    void handlesProcessInstanceMissing_as409() {
        var problem = handler.handleProcessInstanceMissing(
                new WorkflowProcessInstanceMissingException("Workflow link has no process instance"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Workflow process instance missing");
    }

    @Test
    void handlesAccessDenied_as403() {
        var problem = handler.handleAccessDenied(
                new AccessDeniedException("not a participant"),
                new MockHttpServletRequest("GET", "/api/workflow-links/abc/diagram"),
                mock(Authentication.class));

        assertThat(problem.getStatus()).isEqualTo(403);
        assertThat(problem.getTitle()).isEqualTo("Access denied");
    }

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
