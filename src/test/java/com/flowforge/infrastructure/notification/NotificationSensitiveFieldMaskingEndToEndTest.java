package com.flowforge.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flowforge.domain.connector.port.out.ConnectorSecretCipher;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.notification.model.DossierFieldsSnapshot;
import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.model.NotificationOutboxRecord;
import com.flowforge.domain.notification.model.NotificationOutboxStatus;
import com.flowforge.domain.notification.model.NotificationWebhookConfig;
import com.flowforge.domain.notification.port.out.NotificationOutboxRepository;
import com.flowforge.domain.notification.port.out.NotificationWebhookRepository;
import com.flowforge.domain.notification.port.out.PendingOutboxWrite;
import com.flowforge.domain.notification.port.out.UserNotificationRepository;
import com.flowforge.domain.notification.port.out.WebhookPort;
import com.flowforge.domain.notification.usecase.SendNotificationUseCaseImpl;
import com.flowforge.infrastructure.config.NotificationProperties;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * D-WEBHOOK-MASK (masquage-donnees-sensibles-logs-webhooks): proves the masking holds across the
 * *whole* real chain — {@link SendNotificationUseCaseImpl} (builds+masks the payload) →
 * {@link com.flowforge.domain.notification.model.NotificationPayloadJson} (serializes to the
 * outbox row) → {@link NotificationOutboxRelay} (deserializes it back) →
 * {@link WebhookRestAdapter} (the real HTTP body) — not just the use case in isolation. Only the
 * repository/HTTP boundaries are faked; every masking-relevant production class runs for real.
 */
class NotificationSensitiveFieldMaskingEndToEndTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final String SECRET_VALUE = "SECRET-VALUE-DO-NOT-LEAK";

    private static final OutboundUrlGuard.HostResolver FAKE_RESOLVER = host -> {
        if ("hooks.example.com".equals(host.toLowerCase(Locale.ROOT))) {
            return InetAddress.getAllByName("93.184.216.34");
        }
        throw new UnknownHostException(host);
    };

    @Test
    void sensitiveDossierFieldValue_neverReachesTheOutboundHttpBody() {
        UUID webhookId = UUID.randomUUID();
        String tenantId = "tenant_dev";
        Instant now = Instant.parse("2026-08-24T10:00:00Z");

        // 1) SendNotificationUseCaseImpl — real class, masks before writing the outbox row.
        DossierTypeRepository dossierTypeRepository = mock(DossierTypeRepository.class);
        when(dossierTypeRepository.findByCode(tenantId, "CLAIM")).thenReturn(Optional.of(
                DossierType.inherit(UUID.randomUUID(), tenantId, "CLAIM", "Claim", "wf", true, true, List.of(
                        new FieldDefinition("ssn", "SSN", FieldType.STRING, false, null, List.of(), null, true)))));
        NotificationOutboxRepository outboxRepository = mock(NotificationOutboxRepository.class);
        ArgumentCaptor<List<PendingOutboxWrite>> writesCaptor = ArgumentCaptor.forClass(List.class);
        when(outboxRepository.savePendingBatch(any(), writesCaptor.capture(), any()))
                .thenReturn(List.of(UUID.randomUUID()));
        NotificationWebhookRepository webhookRepository = mock(NotificationWebhookRepository.class);
        when(webhookRepository.findByIdAndTenantId(webhookId, tenantId)).thenReturn(Optional.of(
                new NotificationWebhookConfig(
                        webhookId, tenantId, "hook", "https://hooks.example.com/notify",
                        null, true, null, now, now)));

        var useCase = new SendNotificationUseCaseImpl(
                outboxRepository, webhookRepository, dossierTypeRepository, mock(UserNotificationRepository.class));
        useCase.send(
                new NotificationEvent(
                        List.of(NotificationChannel.WEBHOOK),
                        null, null, null, null, webhookId,
                        Map.of(),
                        new DossierFieldsSnapshot("CLAIM", Map.of("ssn", SECRET_VALUE))),
                tenantId);

        String payloadJson = writesCaptor.getValue().getFirst().payloadJson();
        assertThat(payloadJson).doesNotContain(SECRET_VALUE);

        // 2) NotificationOutboxRelay — real class, deserializes the (already masked) outbox row
        //    and dispatches to WebhookPort.
        WebhookPort capturingPort = mock(WebhookPort.class);
        var relay = new NotificationOutboxRelay(
                mock(NotificationOutboxRepository.class),
                webhookRepository,
                (to, subject, body, attachment) -> { },
                capturingPort,
                (userId, event) -> true,
                mock(ConnectorSecretCipher.class),
                new NotificationProperties(
                        new NotificationProperties.Outbox(10, 3, Duration.ofSeconds(1), Duration.ofDays(7)),
                        null,
                        null,
                        null),
                MAPPER,
                mock(com.flowforge.domain.document.port.out.DocumentStoragePort.class),
                new com.flowforge.infrastructure.config.NotificationEmailProperties(0));
        NotificationOutboxRecord record = new NotificationOutboxRecord(
                UUID.randomUUID(), tenantId, NotificationChannel.WEBHOOK, NotificationOutboxStatus.PENDING,
                payloadJson, 0, null, null, now, now, null);
        relay.processOne(record, now);

        var commandCaptor = ArgumentCaptor.forClass(
                com.flowforge.domain.notification.model.WebhookDeliveryCommand.class);
        org.mockito.Mockito.verify(capturingPort).send(commandCaptor.capture());
        assertThat(commandCaptor.getValue().payload().toString()).doesNotContain(SECRET_VALUE);

        // 3) WebhookRestAdapter — real class, the actual outbound HTTP body.
        List<byte[]> httpBodies = new ArrayList<>();
        var adapter = new WebhookRestAdapter(
                new OutboundUrlGuard(false, FAKE_RESOLVER), MAPPER,
                (url, body, headers) -> httpBodies.add(body));
        adapter.send(commandCaptor.getValue());

        assertThat(httpBodies).hasSize(1);
        assertThat(new String(httpBodies.getFirst(), java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain(SECRET_VALUE);
    }
}
