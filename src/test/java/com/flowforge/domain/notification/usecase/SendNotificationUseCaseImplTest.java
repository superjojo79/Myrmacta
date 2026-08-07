package com.flowforge.domain.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.model.NotificationValidationException;
import com.flowforge.domain.notification.model.NotificationWebhookConfig;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.notification.port.out.NotificationOutboxRepository;
import com.flowforge.domain.notification.port.out.NotificationWebhookRepository;
import com.flowforge.domain.notification.port.out.PendingOutboxWrite;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SendNotificationUseCaseImplTest {

    private NotificationOutboxRepository outboxRepository;
    private NotificationWebhookRepository webhookRepository;
    private SendNotificationUseCase useCase;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(NotificationOutboxRepository.class);
        webhookRepository = mock(NotificationWebhookRepository.class);
        useCase = new SendNotificationUseCaseImpl(outboxRepository, webhookRepository);
        when(outboxRepository.savePendingBatch(anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<PendingOutboxWrite> writes = invocation.getArgument(1);
                    return writes.stream().map(w -> UUID.randomUUID()).toList();
                });
    }

    @Test
    void send_emailAndWebhook_persistsTwoPendingRowsInOneBatch() {
        UUID webhookId = UUID.randomUUID();
        stubActiveWebhook(webhookId);

        NotificationEvent event = new NotificationEvent(
                List.of(NotificationChannel.EMAIL, NotificationChannel.WEBHOOK),
                "a@example.com",
                "Hello",
                "Body",
                null,
                webhookId,
                Map.of("k", "v"));

        List<UUID> ids = useCase.send(event, "tenant_dev");

        assertThat(ids).hasSize(2);
        ArgumentCaptor<List<PendingOutboxWrite>> writesCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).savePendingBatch(eq("tenant_dev"), writesCaptor.capture(), any());
        assertThat(writesCaptor.getValue())
                .extracting(PendingOutboxWrite::channel)
                .containsExactly(NotificationChannel.EMAIL, NotificationChannel.WEBHOOK);
    }

    @Test
    void send_emptyChannels_throwsValidation() {
        NotificationEvent event = new NotificationEvent(
                List.of(), "a@example.com", "s", "b", null, null, Map.of());

        assertThatThrownBy(() -> useCase.send(event, "tenant_dev"))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageContaining("channels");
        verify(outboxRepository, never()).savePendingBatch(anyString(), any(), any());
    }

    @Test
    void send_nullChannels_throwsValidation() {
        NotificationEvent event = new NotificationEvent(
                null, "a@example.com", "s", "b", null, null, Map.of());

        assertThatThrownBy(() -> useCase.send(event, "tenant_dev"))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageContaining("channels");
    }

    @Test
    void send_emailMissingFields_throwsValidation() {
        NotificationEvent missingTo = new NotificationEvent(
                List.of(NotificationChannel.EMAIL), "  ", "s", "b", null, null, Map.of());
        assertThatThrownBy(() -> useCase.send(missingTo, "tenant_dev"))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageContaining("to");

        NotificationEvent missingSubject = new NotificationEvent(
                List.of(NotificationChannel.EMAIL), "a@b.c", null, "b", null, null, Map.of());
        assertThatThrownBy(() -> useCase.send(missingSubject, "tenant_dev"))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageContaining("subject");

        NotificationEvent missingBody = new NotificationEvent(
                List.of(NotificationChannel.EMAIL), "a@b.c", "s", " ", null, null, Map.of());
        assertThatThrownBy(() -> useCase.send(missingBody, "tenant_dev"))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageContaining("body");
        verify(outboxRepository, never()).savePendingBatch(anyString(), any(), any());
    }

    @Test
    void send_webhookMissingConfigId_throwsValidation() {
        NotificationEvent event = new NotificationEvent(
                List.of(NotificationChannel.WEBHOOK),
                null,
                null,
                null,
                null,
                null,
                Map.of());

        assertThatThrownBy(() -> useCase.send(event, "tenant_dev"))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageContaining("webhookConfigId");
    }

    @Test
    void send_webhookDisabledOrMissing_throwsValidation() {
        UUID webhookId = UUID.randomUUID();
        when(webhookRepository.findByIdAndTenantId(webhookId, "tenant_dev"))
                .thenReturn(Optional.empty());

        NotificationEvent missing = new NotificationEvent(
                List.of(NotificationChannel.WEBHOOK),
                null,
                null,
                null,
                null,
                webhookId,
                Map.of());
        assertThatThrownBy(() -> useCase.send(missing, "tenant_dev"))
                .isInstanceOf(NotificationValidationException.class);

        Instant now = Instant.parse("2026-07-31T10:00:00Z");
        when(webhookRepository.findByIdAndTenantId(webhookId, "tenant_dev"))
                .thenReturn(Optional.of(new NotificationWebhookConfig(
                        webhookId,
                        "tenant_dev",
                        "hook",
                        "https://example.com/hook",
                        "enc:v1:x",
                        false,
                        null,
                        now,
                        now)));
        assertThatThrownBy(() -> useCase.send(missing, "tenant_dev"))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageContaining("enabled");
    }

    @Test
    void send_browserMissingUserId_throwsValidation() {
        NotificationEvent event = new NotificationEvent(
                List.of(NotificationChannel.BROWSER),
                null,
                null,
                null,
                "  ",
                null,
                Map.of());

        assertThatThrownBy(() -> useCase.send(event, "tenant_dev"))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void send_mixedChannels_validatesAllBeforeAnyWrite() {
        // EMAIL ok, WEBHOOK missing → no batch write
        NotificationEvent event = new NotificationEvent(
                List.of(NotificationChannel.EMAIL, NotificationChannel.WEBHOOK),
                "a@example.com",
                "Hello",
                "Body",
                null,
                null,
                Map.of());

        assertThatThrownBy(() -> useCase.send(event, "tenant_dev"))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageContaining("webhookConfigId");
        verify(outboxRepository, never()).savePendingBatch(anyString(), any(), any());
    }

    @Test
    void send_nullPayload_treatedAsEmptyObjectInSnapshots() {
        UUID webhookId = UUID.randomUUID();
        stubActiveWebhook(webhookId);

        NotificationEvent event = new NotificationEvent(
                List.of(NotificationChannel.WEBHOOK, NotificationChannel.BROWSER),
                null,
                null,
                null,
                "user-1",
                webhookId,
                null);

        useCase.send(event, "tenant_dev");

        ArgumentCaptor<List<PendingOutboxWrite>> writesCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).savePendingBatch(eq("tenant_dev"), writesCaptor.capture(), any());
        assertThat(writesCaptor.getValue())
                .extracting(PendingOutboxWrite::payloadJson)
                .allSatisfy(json -> assertThat(json).contains("\"payload\":{}"));
    }

    @Test
    void send_duplicateChannels_dedupedOrderPreserving() {
        UUID webhookId = UUID.randomUUID();
        stubActiveWebhook(webhookId);

        NotificationEvent event = new NotificationEvent(
                List.of(
                        NotificationChannel.EMAIL,
                        NotificationChannel.WEBHOOK,
                        NotificationChannel.EMAIL,
                        NotificationChannel.BROWSER,
                        NotificationChannel.WEBHOOK),
                "a@example.com",
                "Hello",
                "Body",
                "user-1",
                webhookId,
                Map.of());

        List<UUID> ids = useCase.send(event, "tenant_dev");

        assertThat(ids).hasSize(3);
        ArgumentCaptor<List<PendingOutboxWrite>> writesCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).savePendingBatch(eq("tenant_dev"), writesCaptor.capture(), any());
        assertThat(writesCaptor.getValue())
                .extracting(PendingOutboxWrite::channel)
                .containsExactly(
                        NotificationChannel.EMAIL,
                        NotificationChannel.WEBHOOK,
                        NotificationChannel.BROWSER);
    }

    @Test
    void send_emailSnapshotContainsToSubjectBody() {
        NotificationEvent event = new NotificationEvent(
                List.of(NotificationChannel.EMAIL),
                "a@example.com",
                "Hello \"world\"",
                "Line\n2",
                null,
                null,
                Map.of("ignored", true));

        useCase.send(event, "tenant_dev");

        ArgumentCaptor<List<PendingOutboxWrite>> writesCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).savePendingBatch(eq("tenant_dev"), writesCaptor.capture(), any());
        String json = writesCaptor.getValue().getFirst().payloadJson();
        assertThat(json).contains("\"to\":\"a@example.com\"");
        assertThat(json).contains("\"subject\":\"Hello \\\"world\\\"\"");
        assertThat(json).contains("\"body\":\"Line\\n2\"");
        assertThat(json).doesNotContain("ignored");
    }

    private void stubActiveWebhook(UUID webhookId) {
        Instant now = Instant.parse("2026-07-31T10:00:00Z");
        when(webhookRepository.findByIdAndTenantId(webhookId, "tenant_dev"))
                .thenReturn(Optional.of(new NotificationWebhookConfig(
                        webhookId,
                        "tenant_dev",
                        "hook",
                        "https://example.com/hook",
                        "enc:v1:x",
                        true,
                        null,
                        now,
                        now)));
    }
}
