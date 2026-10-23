package com.flowforge.infrastructure.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.connector.port.out.ConnectorSecretCipher;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationOutboxRecord;
import com.flowforge.domain.notification.model.NotificationOutboxStatus;
import com.flowforge.domain.notification.model.NotificationWebhookConfig;
import com.flowforge.domain.notification.model.WebhookDeliveryCommand;
import com.flowforge.domain.notification.port.out.BrowserPushPort;
import com.flowforge.domain.notification.port.out.EmailPort;
import com.flowforge.domain.notification.port.out.NotificationOutboxRepository;
import com.flowforge.domain.notification.port.out.NotificationWebhookRepository;
import com.flowforge.domain.notification.port.out.WebhookPort;
import com.flowforge.infrastructure.config.NotificationEmailProperties;
import com.flowforge.infrastructure.config.NotificationProperties;
import com.flowforge.infrastructure.observability.MdcScope;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

class NotificationOutboxRelayTest {

    private NotificationOutboxRepository outboxRepository;
    private NotificationWebhookRepository webhookRepository;
    private EmailPort emailPort;
    private DocumentStoragePort storage;
    private WebhookPort webhookPort;
    private BrowserPushPort browserPushPort;
    private ConnectorSecretCipher cipher;
    private NotificationOutboxRelay relay;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @BeforeEach
    void setUp() {
        outboxRepository = mock(NotificationOutboxRepository.class);
        when(outboxRepository.renewClaim(any(), any(), any())).thenReturn(true);
        webhookRepository = mock(NotificationWebhookRepository.class);
        emailPort = mock(EmailPort.class);
        storage = mock(DocumentStoragePort.class);
        webhookPort = mock(WebhookPort.class);
        browserPushPort = mock(BrowserPushPort.class);
        cipher = mock(ConnectorSecretCipher.class);
        NotificationProperties properties = new NotificationProperties(
                new NotificationProperties.Outbox(20, 3, Duration.ofSeconds(1), Duration.ofDays(7)),
                null,
                null,
                null);
        relay = new NotificationOutboxRelay(
                outboxRepository,
                webhookRepository,
                emailPort,
                webhookPort,
                browserPushPort,
                cipher,
                properties,
                JsonMapper.builder().build(),
                storage,
                new NotificationEmailProperties(16));
    }

    /** Issue #183 (D-NOTIF-ATTACH-REF-IN-OUTBOX) — le contenu est relu dans le stockage du tenant de la ligne au moment de l'envoi. */
    @Test
    void relay_emailWithAttachment_readsTheContentFromTheStorageAtSendTime() {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        byte[] pdf = "%PDF-1.7 x".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(storage.retrieve("tenant_dev", "tenant_dev/doc.pdf")).thenReturn(pdf);
        NotificationOutboxRecord record = pending(NotificationChannel.EMAIL, ATTACHMENT_JSON, now);
        when(outboxRepository.lockNextPending(20, now)).thenReturn(List.of(record));

        relay.relayPending(now);

        ArgumentCaptor<EmailPort.EmailAttachmentContent> captor =
                ArgumentCaptor.forClass(EmailPort.EmailAttachmentContent.class);
        verify(emailPort).send(eq("a@example.com"), eq("Hi"), eq("Body"), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().fileName()).isEqualTo("autorisation.pdf");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().contentType()).isEqualTo("application/pdf");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().content()).isEqualTo(pdf);
        verify(outboxRepository).markProcessed(record.id(), now);
    }

    /** Plafond revérifié à l'envoi (il a pu baisser) ; document purgé entre-temps : échec ordinaire, l'outbox retente. */
    @Test
    void relay_emailWithAttachment_tooLargeOrMissingIsAFailureAndNothingIsSent() {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        NotificationOutboxRecord record = pending(NotificationChannel.EMAIL, ATTACHMENT_JSON, now);
        when(outboxRepository.lockNextPending(20, now)).thenReturn(List.of(record));

        when(storage.retrieve("tenant_dev", "tenant_dev/doc.pdf")).thenReturn(new byte[17]);
        relay.relayPending(now);
        org.mockito.Mockito.doThrow(new com.flowforge.domain.document.model.DocumentStorageException("gone"))
                .when(storage).retrieve("tenant_dev", "tenant_dev/doc.pdf");
        relay.relayPending(now);

        verify(emailPort, never()).send(anyString(), anyString(), anyString(), any());
        verify(outboxRepository).recordFailure(eq(record.id()), org.mockito.ArgumentMatchers.contains("exceeds 16 bytes"),
                eq(3), eq(now));
        verify(outboxRepository).recordFailure(eq(record.id()), eq("gone"), eq(3), eq(now));
        verify(outboxRepository, never()).markProcessed(any(), any());
    }

    private static final String ATTACHMENT_JSON = "{\"to\":\"a@example.com\",\"subject\":\"Hi\",\"body\":\"Body\","
            + "\"attachment\":{\"storageRef\":\"tenant_dev/doc.pdf\",\"fileName\":\"autorisation.pdf\","
            + "\"contentType\":\"application/pdf\"}}";

    @Test
    void relay_emailSuccess_marksProcessed() {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        NotificationOutboxRecord record = pending(
                NotificationChannel.EMAIL,
                "{\"to\":\"a@example.com\",\"subject\":\"Hi\",\"body\":\"Body\"}",
                now);
        when(outboxRepository.lockNextPending(20, now)).thenReturn(List.of(record));

        relay.relayPending(now);

        verify(emailPort).send("a@example.com", "Hi", "Body");
        verify(outboxRepository).markProcessed(record.id(), now);
        verify(outboxRepository).purgeTerminalOlderThan(now.minus(Duration.ofDays(7)));
    }

    @Test
    void relay_putsDossierIdFromPayloadThenClearsMdc() {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        UUID dossierId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        NotificationOutboxRecord record = pending(
                NotificationChannel.EMAIL,
                "{\"to\":\"a@example.com\",\"subject\":\"Hi\",\"body\":\"Body\",\"dossierId\":\""
                        + dossierId
                        + "\"}",
                now);
        when(outboxRepository.lockNextPending(20, now)).thenReturn(List.of(record));
        AtomicReference<String> tenantDuring = new AtomicReference<>();
        AtomicReference<String> dossierDuring = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
                    tenantDuring.set(MDC.get(MdcScope.TENANT_ID));
                    dossierDuring.set(MDC.get(MdcScope.DOSSIER_ID));
                    return null;
                })
                .when(emailPort)
                .send(anyString(), anyString(), anyString());

        relay.relayPending(now);

        org.assertj.core.api.Assertions.assertThat(tenantDuring.get()).isEqualTo("tenant_dev");
        org.assertj.core.api.Assertions.assertThat(dossierDuring.get()).isEqualTo(dossierId.toString());
        org.assertj.core.api.Assertions.assertThat(MDC.get(MdcScope.TENANT_ID)).isNull();
        org.assertj.core.api.Assertions.assertThat(MDC.get(MdcScope.DOSSIER_ID)).isNull();
    }

    @Test
    void relay_webhookSuccess_decryptsSecretAndDelivers() {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        UUID webhookId = UUID.randomUUID();
        NotificationOutboxRecord record = pending(
                NotificationChannel.WEBHOOK,
                "{\"webhookConfigId\":\"" + webhookId + "\",\"payload\":{\"k\":\"v\"}}",
                now);
        NotificationWebhookConfig config = new NotificationWebhookConfig(
                webhookId,
                "tenant_dev",
                "hook",
                "https://example.com/hook",
                "enc:cipher",
                true,
                null,
                now,
                now);
        when(outboxRepository.lockNextPending(20, now)).thenReturn(List.of(record));
        when(webhookRepository.findByIdAndTenantId(webhookId, "tenant_dev")).thenReturn(Optional.of(config));
        when(cipher.decrypt("tenant_dev", "enc:cipher")).thenReturn("plain-secret");

        relay.relayPending(now);

        ArgumentCaptor<WebhookDeliveryCommand> captor = ArgumentCaptor.forClass(WebhookDeliveryCommand.class);
        verify(webhookPort).send(captor.capture());
        WebhookDeliveryCommand cmd = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(cmd.targetUrl()).isEqualTo("https://example.com/hook");
        org.assertj.core.api.Assertions.assertThat(cmd.secretPlain()).isEqualTo("plain-secret");
        org.assertj.core.api.Assertions.assertThat(cmd.outboxId()).isEqualTo(record.id());
        org.assertj.core.api.Assertions.assertThat(cmd.payload()).containsEntry("k", "v");
        verify(outboxRepository).markProcessed(record.id(), now);
    }

    @Test
    void relay_webhookMissingOrDisabled_marksDead() {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        UUID webhookId = UUID.randomUUID();
        NotificationOutboxRecord record = pending(
                NotificationChannel.WEBHOOK,
                "{\"webhookConfigId\":\"" + webhookId + "\",\"payload\":{}}",
                now);
        when(outboxRepository.lockNextPending(20, now)).thenReturn(List.of(record));
        when(webhookRepository.findByIdAndTenantId(webhookId, "tenant_dev")).thenReturn(Optional.empty());

        relay.relayPending(now);

        verify(webhookPort, never()).send(any());
        verify(outboxRepository).markDead(eq(record.id()), anyString(), eq(now));
        verify(outboxRepository, never()).recordFailure(any(), anyString(), anyInt(), any());
    }

    @Test
    void relay_webhookDeliveryFailure_recordsFailure() {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        UUID webhookId = UUID.randomUUID();
        NotificationOutboxRecord record = pending(
                NotificationChannel.WEBHOOK,
                "{\"webhookConfigId\":\"" + webhookId + "\",\"payload\":{}}",
                now);
        NotificationWebhookConfig config = new NotificationWebhookConfig(
                webhookId,
                "tenant_dev",
                "hook",
                "https://example.com/hook",
                null,
                true,
                null,
                now,
                now);
        when(outboxRepository.lockNextPending(20, now)).thenReturn(List.of(record));
        when(webhookRepository.findByIdAndTenantId(webhookId, "tenant_dev")).thenReturn(Optional.of(config));
        org.mockito.Mockito.doThrow(new IllegalStateException("upstream 500"))
                .when(webhookPort)
                .send(any());

        relay.relayPending(now);

        verify(outboxRepository).recordFailure(record.id(), "upstream 500", 3, now);
        verify(outboxRepository, never()).markProcessed(any(), any());
    }

    @Test
    void relay_browserDelivered_marksProcessed() {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        UUID inboxId = UUID.randomUUID();
        NotificationOutboxRecord record = pending(
                NotificationChannel.BROWSER,
                "{\"userId\":\"user-1\",\"inboxId\":\"" + inboxId + "\",\"payload\":{\"a\":1}}",
                now);
        when(outboxRepository.lockNextPending(20, now)).thenReturn(List.of(record));
        when(browserPushPort.push(eq("user-1"), any())).thenReturn(true);

        relay.relayPending(now);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(browserPushPort).push(eq("user-1"), eventCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(eventCaptor.getValue())
                // D-NOTIF-SSE-ID: the event id must be the user_notification row id (inboxId),
                // never the outbox row's own id — the client PATCHes /read against this exact id.
                .containsEntry("id", inboxId.toString())
                .containsKey("payload")
                .containsKey("at");
        verify(outboxRepository).markProcessed(record.id(), now);
    }

    @Test
    void relay_browserSkipped_marksProcessed() {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        NotificationOutboxRecord record = pending(
                NotificationChannel.BROWSER,
                "{\"userId\":\"user-1\",\"inboxId\":\"" + UUID.randomUUID() + "\",\"payload\":{}}",
                now);
        when(outboxRepository.lockNextPending(20, now)).thenReturn(List.of(record));
        when(browserPushPort.push(eq("user-1"), any())).thenReturn(false);

        relay.relayPending(now);

        verify(outboxRepository).markProcessed(record.id(), now);
        verify(outboxRepository, never()).markDead(any(), anyString(), any());
    }

    @Test
    void relay_invalidPayloadJson_recordsFailure() {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        NotificationOutboxRecord record =
                pending(NotificationChannel.EMAIL, "{not-json", now);
        when(outboxRepository.lockNextPending(20, now)).thenReturn(List.of(record));

        relay.relayPending(now);

        verify(emailPort, never()).send(anyString(), anyString(), anyString());
        verify(outboxRepository).recordFailure(eq(record.id()), anyString(), eq(3), eq(now));
    }

    private static NotificationOutboxRecord pending(
            NotificationChannel channel, String payloadJson, Instant now) {
        return new NotificationOutboxRecord(
                UUID.randomUUID(),
                "tenant_dev",
                channel,
                NotificationOutboxStatus.PENDING,
                payloadJson,
                0,
                now,
                null,
                now,
                now,
                null);
    }

    // ---- TODO §79 (D5, revue) : le bail est renouvelé juste avant chaque envoi -----------------------------------------

    @Test
    void relay_aRowTakenOverByAnotherExecutorWhileTheBatchWasRunningIsSkippedNotSentTwice() {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        NotificationOutboxRecord taken = pending(NotificationChannel.EMAIL,
                "{\"to\":\"a@b.c\",\"subject\":\"s\",\"body\":\"b\"}", now);
        when(outboxRepository.lockNextPending(20, now)).thenReturn(List.of(taken));
        when(outboxRepository.renewClaim(eq(taken.id()), eq(taken.nextAttemptAt()), any())).thenReturn(false);

        relay.relayPending(now);

        verify(emailPort, never()).send(any(), any(), any());
        verify(outboxRepository, never()).markProcessed(any(), any());
    }

    @Test
    void relay_renewsTheClaimWithTheTokenItReceivedBeforeSending() {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        NotificationOutboxRecord row = pending(NotificationChannel.EMAIL,
                "{\"to\":\"a@b.c\",\"subject\":\"s\",\"body\":\"b\"}", now);
        when(outboxRepository.lockNextPending(20, now)).thenReturn(List.of(row));

        relay.relayPending(now);

        var order = org.mockito.Mockito.inOrder(outboxRepository, emailPort);
        var renewedAt = org.mockito.ArgumentCaptor.forClass(Instant.class);
        order.verify(outboxRepository).renewClaim(eq(row.id()), eq(row.nextAttemptAt()), renewedAt.capture());
        // revue D5 passe 2 : l'instant de renouvellement est frais (jamais le `now` capturé au début du lot)
        org.assertj.core.api.Assertions.assertThat(renewedAt.getValue()).isAfter(now);
        order.verify(emailPort).send("a@b.c", "s", "b");
        order.verify(outboxRepository).markProcessed(row.id(), now);
    }
}
