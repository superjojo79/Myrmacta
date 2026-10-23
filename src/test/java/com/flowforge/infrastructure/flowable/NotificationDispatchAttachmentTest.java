package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.notification.model.EmailAttachment;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.model.NotificationValidationException;
import com.flowforge.domain.notification.port.in.ResolveEmailAttachmentUseCase;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.workflow.portal.model.PortalRecipientSource;
import com.flowforge.domain.workflow.portal.port.out.PortalRecipientResolverPort;
import com.flowforge.infrastructure.config.TenantProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Issue #183 — pièce jointe de la brique « Notifier » (D-NOTIF-ATTACH-LITERAL, D-NOTIF-ATTACH-PRODUCED-DOC). */
class NotificationDispatchAttachmentTest {

    private static final String REF = "tenant_dev/doc-1.pdf";
    private static final EmailAttachment ATTACHMENT = new EmailAttachment(REF, "autorisation.pdf", "application/pdf");

    private final SendNotificationUseCase send = mock(SendNotificationUseCase.class);
    private final PortalRecipientResolverPort resolver = mock(PortalRecipientResolverPort.class);
    private final ResolveEmailAttachmentUseCase attachments = mock(ResolveEmailAttachmentUseCase.class);
    private final NotificationDispatchTask task =
            new NotificationDispatchTask(new TenantProperties("tenant_dev"), send, resolver, attachments);
    private final UUID dossierId = UUID.randomUUID();

    @Test
    void aVariableSourceIsResolvedThenCheckedAgainstTheDossierAndTravelsWithTheEmail() {
        when(attachments.resolve(dossierId, REF, "autorisation",
                "notificationAttachment source variable:generatedDocumentId")).thenReturn(ATTACHMENT);

        task.execute(execution(Map.of("generatedDocumentId", REF), Map.of(
                "notificationAttachment", "variable:generatedDocumentId", "notificationAttachmentName", "autorisation")));

        assertThat(sentEvent().attachment()).isEqualTo(ATTACHMENT);
    }

    @Test
    void aDossierFieldSourceIsReadThroughTheResolver() {
        PortalRecipientSource field = new PortalRecipientSource(PortalRecipientSource.Kind.DOSSIER_FIELD, "decision_pdf");
        when(resolver.resolve(field, dossierId, "pi-1")).thenReturn(Optional.of(REF));
        when(attachments.resolve(dossierId, REF, null, "notificationAttachment source dossierField:decision_pdf"))
                .thenReturn(ATTACHMENT);

        task.execute(execution(Map.of(), Map.of("notificationAttachment", "dossierField:decision_pdf")));

        assertThat(sentEvent().attachment()).isEqualTo(ATTACHMENT);
    }

    @Test
    void withoutTheAttributeThereIsNoAttachment_evenIfAUserSetTheVariables() {
        // Variables posées à la complétion d'une tâche : jamais lues (D-NOTIF-ATTACH-LITERAL).
        task.execute(execution(Map.of("notificationAttachment", "variable:secret", "secret", "tenant_dev/autre.pdf",
                "notificationAttachmentName", "x"), Map.of()));

        assertThat(sentEvent().attachment()).isNull();
        verifyNoInteractions(attachments);
    }

    @Test
    void anUnresolvedOrNonTextSourceFailsNamingTheSourceAndNothingIsSent() {
        assertThatThrownBy(() -> task.execute(execution(Map.of(),
                Map.of("notificationAttachment", "variable:generatedDocumentId"))))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessage("notificationAttachment source variable:generatedDocumentId resolved to no value");
        assertThatThrownBy(() -> task.execute(execution(Map.of("generatedDocumentId", 42),
                Map.of("notificationAttachment", "variable:generatedDocumentId"))))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageContaining("is not a text value");
        verify(send, never()).send(any(), anyString());
    }

    @Test
    void theResolverRefusalPropagatesAndNothingIsSent() {
        when(attachments.resolve(eq(dossierId), eq(REF), any(), anyString()))
                .thenThrow(new NotificationValidationException("refused"));

        assertThatThrownBy(() -> task.execute(execution(Map.of("generatedDocumentId", REF),
                Map.of("notificationAttachment", "variable:generatedDocumentId"))))
                .isInstanceOf(NotificationValidationException.class).hasMessage("refused");
        verify(send, never()).send(any(), anyString());
    }

    private NotificationEvent sentEvent() {
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(send).send(captor.capture(), eq("tenant_dev"));
        return captor.getValue();
    }

    private DelegateExecution execution(Map<String, Object> variables, Map<String, String> extraAttributes) {
        Map<String, Object> vars = new LinkedHashMap<>(variables);
        vars.put("dossierId", dossierId.toString());
        Map<String, String> attributes = new LinkedHashMap<>(Map.of("notificationChannels", "EMAIL",
                "notificationTo", "camille@example.org", "notificationSubject", "Décision", "notificationBody", "Ci-joint."));
        attributes.putAll(extraAttributes);
        DelegateExecution execution = mock(DelegateExecution.class);
        vars.forEach((k, v) -> when(execution.getVariable(k)).thenReturn(v));
        when(execution.getProcessInstanceId()).thenReturn("pi-1");
        ServiceTask serviceTask = new ServiceTask();
        serviceTask.setId("act");
        attributes.forEach((k, v) -> DeployedActivityParametersTest.taskWith(k, v)
                .getAttributes().forEach((name, list) -> list.forEach(serviceTask::addAttribute)));
        when(execution.getCurrentFlowElement()).thenReturn(serviceTask);
        return execution;
    }
}
