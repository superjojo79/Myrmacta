package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationDispatchTaskTest {

    private SendNotificationUseCase sendNotificationUseCase;
    private NotificationDispatchTask task;
    private DelegateExecution execution;

    @BeforeEach
    void setUp() {
        sendNotificationUseCase = mock(SendNotificationUseCase.class);
        task = new NotificationDispatchTask(new TenantProperties("tenant_dev"), sendNotificationUseCase,
                mock(com.flowforge.domain.workflow.portal.port.out.PortalRecipientResolverPort.class),
                mock(com.flowforge.domain.notification.port.in.ResolveEmailAttachmentUseCase.class));
        execution = mock(DelegateExecution.class);
    }

    @Test
    void execute_csvChannels_buildsEventAndCallsUseCase() {
        when(execution.getVariable("notificationChannels")).thenReturn("EMAIL,BROWSER");
        when(execution.getVariable("notificationTo")).thenReturn("a@example.com");
        when(execution.getVariable("notificationSubject")).thenReturn("Subject");
        when(execution.getVariable("notificationBody")).thenReturn("Body");
        when(execution.getVariable("notificationUserId")).thenReturn("user-1");
        when(execution.getVariable("notificationWebhookConfigId")).thenReturn(null);
        when(execution.getVariable("notificationPayload")).thenReturn(Map.of("k", "v"));
        when(sendNotificationUseCase.send(org.mockito.ArgumentMatchers.any(), eq("tenant_dev")))
                .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));

        task.execute(execution);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(sendNotificationUseCase).send(captor.capture(), eq("tenant_dev"));
        NotificationEvent event = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(event.channels())
                .containsExactly(NotificationChannel.EMAIL, NotificationChannel.BROWSER);
        org.assertj.core.api.Assertions.assertThat(event.to()).isEqualTo("a@example.com");
        org.assertj.core.api.Assertions.assertThat(event.subject()).isEqualTo("Subject");
        org.assertj.core.api.Assertions.assertThat(event.body()).isEqualTo("Body");
        org.assertj.core.api.Assertions.assertThat(event.userId()).isEqualTo("user-1");
        org.assertj.core.api.Assertions.assertThat(event.payload()).containsEntry("k", "v");
    }

    @Test
    void execute_listChannels_accepted() {
        when(execution.getVariable("notificationChannels"))
                .thenReturn(List.of("WEBHOOK", "EMAIL"));
        when(execution.getVariable("notificationTo")).thenReturn("a@example.com");
        when(execution.getVariable("notificationSubject")).thenReturn("S");
        when(execution.getVariable("notificationBody")).thenReturn("B");
        UUID webhookId = UUID.randomUUID();
        when(execution.getVariable("notificationWebhookConfigId")).thenReturn(webhookId.toString());
        when(execution.getVariable("notificationUserId")).thenReturn(null);
        when(execution.getVariable("notificationPayload")).thenReturn(null);
        when(sendNotificationUseCase.send(org.mockito.ArgumentMatchers.any(), eq("tenant_dev")))
                .thenReturn(List.of(UUID.randomUUID()));

        task.execute(execution);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(sendNotificationUseCase).send(captor.capture(), eq("tenant_dev"));
        org.assertj.core.api.Assertions.assertThat(captor.getValue().channels())
                .containsExactly(NotificationChannel.WEBHOOK, NotificationChannel.EMAIL);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().webhookConfigId()).isEqualTo(webhookId);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().payload()).isEmpty();
    }

    @Test
    void execute_missingChannels_fails() {
        when(execution.getVariable("notificationChannels")).thenReturn(null);

        assertThatThrownBy(() -> task.execute(execution)).isInstanceOf(IllegalArgumentException.class);
    }
}
