package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.notification.model.NotificationChannel;
import be.myrmacta.domain.notification.model.NotificationEvent;
import be.myrmacta.domain.notification.model.NotificationValidationException;
import be.myrmacta.domain.notification.port.in.ResolveEmailAttachmentUseCase;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.notification.template.model.RenderedNotification;
import be.myrmacta.domain.notification.template.model.TemplateResolutionKey;
import be.myrmacta.domain.workflow.portal.port.out.PortalRecipientResolverPort;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Issue #19 (TODO §123) — la brique « Notifier » avec {@code flowable:notificationTemplate}. */
class NotificationDispatchTemplateTest {

    private final SendNotificationUseCase send = mock(SendNotificationUseCase.class);
    private final NotificationTemplateRendering rendering = mock(NotificationTemplateRendering.class);
    private final NotificationDispatchTask task = new NotificationDispatchTask(new TenantProperties("tenant_dev"), send,
            mock(PortalRecipientResolverPort.class), mock(ResolveEmailAttachmentUseCase.class), rendering);

    @Test
    void theRenderedSubjectTextAndHtmlBecomeTheEmail() {
        when(rendering.render(any(), eq(new TemplateResolutionKey("accuse", "courte", "fr"))))
                .thenReturn(new RenderedNotification("Objet rendu", "Texte rendu", "<p>Html rendu</p>"));

        task.execute(execution(Map.of("variante", "courte"), Map.of("notificationTemplate", "accuse",
                "notificationVariant", "variable:variante", "notificationLocale", "fr")));

        NotificationEvent event = sent();
        assertThat(event.channels()).containsExactly(NotificationChannel.EMAIL);
        assertThat(event.to()).isEqualTo("camille@example.org");
        assertThat(event.subject()).isEqualTo("Objet rendu");
        assertThat(event.body()).isEqualTo("Texte rendu");
        assertThat(event.htmlBody()).isEqualTo("<p>Html rendu</p>");
    }

    @Test
    void anAbsentVariantOrLocaleVariableMeansNoVariantAndNoLocale() {
        when(rendering.render(any(), eq(new TemplateResolutionKey("accuse", null, null))))
                .thenReturn(new RenderedNotification("S", "B", null));

        task.execute(execution(Map.of(), Map.of("notificationTemplate", "accuse",
                "notificationVariant", "variable:variante", "notificationLocale", "variable:langue")));

        assertThat(sent().htmlBody()).isNull();
        assertThat(sent().subject()).isEqualTo("S");
    }

    /** CS4 — le rendu échoue : l'étape échoue et rien n'est envoyé (donc rien dans l'outbox). */
    @Test
    void aFailedRenderFailsTheStepAndNothingIsSent() {
        when(rendering.render(any(), any())).thenThrow(new NotificationValidationException(
                "notificationTemplate accuse: Property or field 'objet' cannot be found"));

        assertThatThrownBy(() -> task.execute(execution(Map.of(), Map.of("notificationTemplate", "accuse"))))
                .isInstanceOf(NotificationValidationException.class).hasMessageContaining("accuse");
        verify(send, never()).send(any(), any());
    }

    /** CS5 — une variable de process {@code notificationTemplate} ne désigne jamais un gabarit. */
    @Test
    void aProcessVariableNamedNotificationTemplateIsIgnored() {
        task.execute(execution(Map.of("notificationTemplate", "autre"), Map.of()));

        verifyNoInteractions(rendering);
        assertThat(sent().subject()).isEqualTo("Objet littéral");
        assertThat(sent().body()).isEqualTo("Corps littéral");
        assertThat(sent().htmlBody()).isNull();
    }

    /** Issue #43 — avec une pièce jointe, le gabarit exige encore EMAIL seul à l'exécution. */
    @Test
    void aTemplateWithAnAttachmentStillRequiresEmailOnly() {
        when(rendering.render(any(), any())).thenReturn(new RenderedNotification("S", "B", null));

        assertThatThrownBy(() -> task.execute(execution(Map.of(), Map.of(
                "notificationTemplate", "accuse",
                "notificationChannels", "EMAIL,BROWSER",
                "notificationAttachment", "variable:doc",
                "notificationAttachmentName", "piece.pdf"))))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageContaining("EMAIL");
        verify(send, never()).send(any(), any());
    }

    /** Défense en profondeur : un BPMN déployé sans le parseur ne peut pas lire canal ou destinataire dans une variable. */
    @Test
    void aTemplateNeedsTheEmailChannelAndTheRecipientInTheDeployedBpmn() {
        assertThatThrownBy(() -> task.execute(execution(Map.of("notificationChannels", "EMAIL"),
                Map.of("notificationTemplate", "accuse"), java.util.Set.of("notificationChannels"))))
                .isInstanceOf(NotificationValidationException.class).hasMessageContaining("notificationTemplate");
        assertThatThrownBy(() -> task.execute(execution(Map.of(), Map.of("notificationTemplate", "accuse",
                "notificationChannels", "EMAIL,BROWSER"))))
                .isInstanceOf(NotificationValidationException.class).hasMessageContaining("EMAIL");
        assertThatThrownBy(() -> task.execute(execution(Map.of("notificationTo", "x@example.org"),
                Map.of("notificationTemplate", "accuse"), java.util.Set.of("notificationTo"))))
                .isInstanceOf(NotificationValidationException.class).hasMessageContaining("notificationTo");
        verify(send, never()).send(any(), any());
    }

    private NotificationEvent sent() {
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(send).send(captor.capture(), eq("tenant_dev"));
        return captor.getValue();
    }

    private DelegateExecution execution(Map<String, Object> variables, Map<String, String> extraAttributes) {
        return execution(variables, extraAttributes, java.util.Set.of());
    }

    private DelegateExecution execution(
            Map<String, Object> variables, Map<String, String> extraAttributes, java.util.Set<String> withoutAttributes) {
        Map<String, Object> vars = new LinkedHashMap<>(variables);
        vars.put("dossierId", UUID.randomUUID().toString());
        Map<String, String> attributes = new LinkedHashMap<>(Map.of("notificationChannels", "EMAIL",
                "notificationTo", "camille@example.org"));
        if (!extraAttributes.containsKey("notificationTemplate")) {
            attributes.put("notificationSubject", "Objet littéral");
            attributes.put("notificationBody", "Corps littéral");
        }
        attributes.putAll(extraAttributes);
        attributes.keySet().removeAll(withoutAttributes);
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
