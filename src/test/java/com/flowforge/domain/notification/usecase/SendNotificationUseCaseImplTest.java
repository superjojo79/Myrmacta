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

import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.notification.model.DossierFieldsSnapshot;
import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.model.NotificationValidationException;
import com.flowforge.domain.notification.model.NotificationWebhookConfig;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.notification.port.out.NotificationOutboxRepository;
import com.flowforge.domain.notification.port.out.NotificationWebhookRepository;
import com.flowforge.domain.notification.port.out.PendingOutboxWrite;
import com.flowforge.domain.notification.port.out.UserNotificationRepository;
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
    private DossierTypeRepository dossierTypeRepository;
    private UserNotificationRepository userNotificationRepository;
    private SendNotificationUseCase useCase;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(NotificationOutboxRepository.class);
        webhookRepository = mock(NotificationWebhookRepository.class);
        dossierTypeRepository = mock(DossierTypeRepository.class);
        userNotificationRepository = mock(UserNotificationRepository.class);
        useCase = new SendNotificationUseCaseImpl(
                outboxRepository, webhookRepository, dossierTypeRepository, userNotificationRepository);
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

    @Test
    void send_noDossierFieldsSnapshot_neverQueriesDossierTypeRepository() {
        // D-WEBHOOK-MASK: the lookup only happens if a producer opts in — today's 2 real
        // producers never do, so this is the 100%-of-traffic non-regression case.
        UUID webhookId = UUID.randomUUID();
        stubActiveWebhook(webhookId);
        NotificationEvent event = new NotificationEvent(
                List.of(NotificationChannel.WEBHOOK), null, null, null, null, webhookId, Map.of("k", "v"));

        useCase.send(event, "tenant_dev");

        verify(dossierTypeRepository, never()).findByCode(any(), any());
    }

    @Test
    void send_dossierFieldsSnapshotWithSensitiveField_valueStrippedFromPayload() {
        UUID webhookId = UUID.randomUUID();
        stubActiveWebhook(webhookId);
        stubDossierType("CLAIM", List.of(
                new FieldDefinition("ssn", "SSN", FieldType.STRING, false, null, List.of(), null, true)));
        NotificationEvent event = new NotificationEvent(
                List.of(NotificationChannel.WEBHOOK),
                null, null, null, null, webhookId,
                Map.of(),
                new DossierFieldsSnapshot("CLAIM", Map.of("ssn", "SECRET-VALUE-DO-NOT-LEAK")));

        useCase.send(event, "tenant_dev");

        String json = capturePayloadJson();
        assertThat(json).doesNotContain("SECRET-VALUE-DO-NOT-LEAK");
    }

    @Test
    void send_dossierFieldsSnapshotWithNonSensitiveField_valueKeptInPayload() {
        UUID webhookId = UUID.randomUUID();
        stubActiveWebhook(webhookId);
        stubDossierType("CLAIM", List.of(
                new FieldDefinition("montant", "Montant", FieldType.NUMBER, false, null, List.of(), null)));
        NotificationEvent event = new NotificationEvent(
                List.of(NotificationChannel.WEBHOOK),
                null, null, null, null, webhookId,
                Map.of(),
                new DossierFieldsSnapshot("CLAIM", Map.of("montant", 42)));

        useCase.send(event, "tenant_dev");

        String json = capturePayloadJson();
        assertThat(json).contains("\"montant\":42");
    }

    @Test
    void send_dossierFieldsSnapshotWithUnknownDossierType_omitsDossierFieldsWithoutThrowing() {
        UUID webhookId = UUID.randomUUID();
        stubActiveWebhook(webhookId);
        when(dossierTypeRepository.findByCode("tenant_dev", "GHOST")).thenReturn(Optional.empty());
        NotificationEvent event = new NotificationEvent(
                List.of(NotificationChannel.WEBHOOK),
                null, null, null, null, webhookId,
                Map.of(),
                new DossierFieldsSnapshot("GHOST", Map.of("ssn", "SECRET-VALUE-DO-NOT-LEAK")));

        useCase.send(event, "tenant_dev");

        String json = capturePayloadJson();
        assertThat(json).doesNotContain("SECRET-VALUE-DO-NOT-LEAK").doesNotContain("dossierFields");
    }

    @Test
    void send_dossierFieldsSnapshotWithSensitiveFieldAbsentFromValues_doesNotThrow() {
        // Sensitive per the type's FieldDefinition, but simply not present in this dossier's
        // field map (matches the "absent key, not a null value" convention used elsewhere —
        // e.g. DossierFieldMerger, DossierFieldRedactor).
        UUID webhookId = UUID.randomUUID();
        stubActiveWebhook(webhookId);
        stubDossierType("CLAIM", List.of(
                new FieldDefinition("ssn", "SSN", FieldType.STRING, false, null, List.of(), null, true)));
        NotificationEvent event = new NotificationEvent(
                List.of(NotificationChannel.WEBHOOK),
                null, null, null, null, webhookId,
                Map.of(),
                new DossierFieldsSnapshot("CLAIM", Map.of("other", "x")));

        useCase.send(event, "tenant_dev");

        String json = capturePayloadJson();
        assertThat(json).contains("\"other\":\"x\"");
    }

    @Test
    void send_browserChannelWithDossierFieldsSnapshot_alsoMasked() {
        stubDossierType("CLAIM", List.of(
                new FieldDefinition("ssn", "SSN", FieldType.STRING, false, null, List.of(), null, true)));
        NotificationEvent event = new NotificationEvent(
                List.of(NotificationChannel.BROWSER),
                null, null, null, "user-1", null,
                Map.of(),
                new DossierFieldsSnapshot("CLAIM", Map.of("ssn", "SECRET-VALUE-DO-NOT-LEAK")));

        useCase.send(event, "tenant_dev");

        String json = capturePayloadJson();
        assertThat(json).doesNotContain("SECRET-VALUE-DO-NOT-LEAK");
    }

    @Test
    void send_browserChannel_alsoPersistsUserNotification() {
        // UT-NOTIF-6
        NotificationEvent event = new NotificationEvent(
                List.of(NotificationChannel.BROWSER), null, null, null, "user-1", null, Map.of("type", "X"));

        useCase.send(event, "tenant_dev");

        verify(userNotificationRepository).save(any(), eq("tenant_dev"), eq("user-1"), anyString(), any());
    }

    @Test
    void send_emailOrWebhookOnly_neverPersistsUserNotification() {
        // UT-NOTIF-7
        UUID webhookId = UUID.randomUUID();
        stubActiveWebhook(webhookId);
        NotificationEvent event = new NotificationEvent(
                List.of(NotificationChannel.EMAIL, NotificationChannel.WEBHOOK),
                "a@example.com",
                "Hello",
                "Body",
                null,
                webhookId,
                Map.of());

        useCase.send(event, "tenant_dev");

        verify(userNotificationRepository, never()).save(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void send_multiChannelIncludingBrowser_writesOneOutboxRowPerChannelAndOneInboxRow() {
        // UT-NOTIF-8
        NotificationEvent event = new NotificationEvent(
                List.of(NotificationChannel.BROWSER, NotificationChannel.EMAIL),
                "a@example.com",
                "Hello",
                "Body",
                "user-1",
                null,
                Map.of());

        List<UUID> ids = useCase.send(event, "tenant_dev");

        assertThat(ids).hasSize(2);
        verify(userNotificationRepository, org.mockito.Mockito.times(1))
                .save(any(), eq("tenant_dev"), eq("user-1"), anyString(), any());
    }

    @Test
    void send_browserChannelPayload_persistedWithoutUserIdWrapper() {
        // Confirms user_notification stores the plain payload, not the {"userId":..,"payload":..}
        // wrapper used for the outbox row (recipient is already a column).
        NotificationEvent event = new NotificationEvent(
                List.of(NotificationChannel.BROWSER), null, null, null, "user-1", null, Map.of("type", "X"));

        useCase.send(event, "tenant_dev");

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(userNotificationRepository).save(any(), eq("tenant_dev"), eq("user-1"), jsonCaptor.capture(), any());
        assertThat(jsonCaptor.getValue()).isEqualTo("{\"type\":\"X\"}");
    }

    @Test
    void send_browserChannel_outboxSnapshotCarriesSameInboxIdAsUserNotificationSave() {
        // D-NOTIF-SSE-ID (F1 fix): the id embedded in the BROWSER outbox snapshot — later
        // surfaced as the SSE event id — must be the exact id the user_notification row is saved
        // under, so a client can PATCH /api/notifications/{id}/read against a live-pushed item.
        NotificationEvent event = new NotificationEvent(
                List.of(NotificationChannel.BROWSER), null, null, null, "user-1", null, Map.of("type", "X"));

        useCase.send(event, "tenant_dev");

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(userNotificationRepository).save(idCaptor.capture(), eq("tenant_dev"), eq("user-1"), anyString(), any());
        String outboxJson = capturePayloadJson();
        assertThat(outboxJson).contains("\"inboxId\":\"" + idCaptor.getValue() + "\"");
    }

    private void stubDossierType(String code, List<FieldDefinition> definitions) {
        when(dossierTypeRepository.findByCode("tenant_dev", code)).thenReturn(Optional.of(
                DossierType.inherit(UUID.randomUUID(), "tenant_dev", code, code, "wf", true, true, definitions)));
    }

    private String capturePayloadJson() {
        ArgumentCaptor<List<PendingOutboxWrite>> writesCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).savePendingBatch(eq("tenant_dev"), writesCaptor.capture(), any());
        return writesCaptor.getValue().getFirst().payloadJson();
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
