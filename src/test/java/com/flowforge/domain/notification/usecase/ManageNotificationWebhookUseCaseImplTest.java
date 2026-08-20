package com.flowforge.domain.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.notification.model.NotificationValidationException;
import com.flowforge.domain.notification.model.NotificationWebhookConfig;
import com.flowforge.domain.notification.model.NotificationWebhookConflictException;
import com.flowforge.domain.notification.model.NotificationWebhookNotFoundException;
import com.flowforge.domain.notification.port.in.ManageNotificationWebhookUseCase;
import com.flowforge.domain.notification.port.out.NotificationWebhookRepository;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ManageNotificationWebhookUseCaseImplTest {

    private NotificationWebhookRepository repository;
    private ManageNotificationWebhookUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationWebhookRepository.class);
        useCase = new ManageNotificationWebhookUseCaseImpl(repository);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void create_persistsWebhookWithPlainSecretAndGeneratedId() {
        when(repository.existsActiveByName(eq("tenant_dev"), eq("Claims"), isNull())).thenReturn(false);

        NotificationWebhookConfig created = useCase.create(
                new ManageNotificationWebhookUseCase.CreateCommand(
                        "tenant_dev",
                        "Claims",
                        "https://example.com/hook",
                        "plain-secret",
                        true));

        assertThat(created.id()).isNotNull();
        assertThat(created.tenantId()).isEqualTo("tenant_dev");
        assertThat(created.name()).isEqualTo("Claims");
        assertThat(created.targetUrl()).isEqualTo("https://example.com/hook");
        assertThat(created.secretCiphertext()).isEqualTo("plain-secret");
        assertThat(created.enabled()).isTrue();
        assertThat(created.deletedAt()).isNull();
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.updatedAt()).isNotNull();
        verify(repository).save(created);
    }

    @Test
    void create_duplicateActiveName_throwsConflict() {
        when(repository.existsActiveByName(eq("tenant_dev"), eq("Claims"), isNull())).thenReturn(true);

        assertThatThrownBy(() -> useCase.create(new ManageNotificationWebhookUseCase.CreateCommand(
                        "tenant_dev", "Claims", "https://example.com/hook", null, true)))
                .isInstanceOf(NotificationWebhookConflictException.class)
                .hasMessageContaining("Claims");
    }

    @Test
    void create_blankNameOrUrl_throwsValidation() {
        assertThatThrownBy(() -> useCase.create(new ManageNotificationWebhookUseCase.CreateCommand(
                        "tenant_dev", "  ", "https://example.com/hook", null, true)))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageContaining("name");

        assertThatThrownBy(() -> useCase.create(new ManageNotificationWebhookUseCase.CreateCommand(
                        "tenant_dev", "Claims", " ", null, true)))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageContaining("targetUrl");
    }

    @Test
    void get_missing_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, "tenant_dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(id, "tenant_dev"))
                .isInstanceOf(NotificationWebhookNotFoundException.class);
    }

    @Test
    void list_delegatesToRepository() {
        Instant now = Instant.parse("2026-07-31T10:00:00Z");
        NotificationWebhookConfig config = new NotificationWebhookConfig(
                UUID.randomUUID(),
                "tenant_dev",
                "Claims",
                "https://example.com/hook",
                "enc:v1:x",
                true,
                null,
                now,
                now);
        when(repository.pageByTenantId("tenant_dev", PageQuery.defaults()))
                .thenReturn(PageResult.of(List.of(config), 0, 20, 1));

        assertThat(useCase.list("tenant_dev", PageQuery.defaults()).content()).containsExactly(config);
    }

    @Test
    void update_mergesFieldsAndPreservesSecretWhenMasked() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-07-31T09:00:00Z");
        NotificationWebhookConfig existing = new NotificationWebhookConfig(
                id,
                "tenant_dev",
                "Old",
                "https://example.com/old",
                "enc:v1:existing",
                false,
                null,
                created,
                created);
        when(repository.findByIdAndTenantId(id, "tenant_dev")).thenReturn(Optional.of(existing));
        when(repository.existsActiveByName("tenant_dev", "New", id)).thenReturn(false);

        NotificationWebhookConfig updated = useCase.update(
                new ManageNotificationWebhookUseCase.UpdateCommand(
                        id,
                        "tenant_dev",
                        "New",
                        "https://example.com/new",
                        ManageNotificationWebhookUseCaseImpl.SECRET_MASK,
                        true,
                        false));

        assertThat(updated.name()).isEqualTo("New");
        assertThat(updated.targetUrl()).isEqualTo("https://example.com/new");
        assertThat(updated.secretCiphertext()).isEqualTo("enc:v1:existing");
        assertThat(updated.enabled()).isTrue();
        assertThat(updated.createdAt()).isEqualTo(created);
        assertThat(updated.updatedAt()).isAfterOrEqualTo(created);
    }

    @Test
    void update_blankSecretKeepsExistingCiphertext() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-31T10:00:00Z");
        NotificationWebhookConfig existing = new NotificationWebhookConfig(
                id,
                "tenant_dev",
                "Claims",
                "https://example.com/hook",
                "enc:v1:existing",
                true,
                null,
                now,
                now);
        when(repository.findByIdAndTenantId(id, "tenant_dev")).thenReturn(Optional.of(existing));
        when(repository.existsActiveByName("tenant_dev", "Claims", id)).thenReturn(false);

        NotificationWebhookConfig updated = useCase.update(
                new ManageNotificationWebhookUseCase.UpdateCommand(
                        id, "tenant_dev", null, null, "  ", null, false));

        assertThat(updated.secretCiphertext()).isEqualTo("enc:v1:existing");
        assertThat(updated.name()).isEqualTo("Claims");
        assertThat(updated.targetUrl()).isEqualTo("https://example.com/hook");
        assertThat(updated.enabled()).isTrue();
    }

    @Test
    void update_duplicateName_throwsConflict() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-31T10:00:00Z");
        NotificationWebhookConfig existing = new NotificationWebhookConfig(
                id,
                "tenant_dev",
                "Old",
                "https://example.com/hook",
                null,
                true,
                null,
                now,
                now);
        when(repository.findByIdAndTenantId(id, "tenant_dev")).thenReturn(Optional.of(existing));
        when(repository.existsActiveByName("tenant_dev", "Taken", id)).thenReturn(true);

        assertThatThrownBy(() -> useCase.update(new ManageNotificationWebhookUseCase.UpdateCommand(
                        id, "tenant_dev", "Taken", null, null, null, false)))
                .isInstanceOf(NotificationWebhookConflictException.class);
    }

    @Test
    void softDelete_requiresExistingActiveWebhook() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-31T10:00:00Z");
        when(repository.findByIdAndTenantId(id, "tenant_dev"))
                .thenReturn(Optional.of(new NotificationWebhookConfig(
                        id,
                        "tenant_dev",
                        "Claims",
                        "https://example.com/hook",
                        null,
                        true,
                        null,
                        now,
                        now)));

        useCase.softDelete(id, "tenant_dev");

        verify(repository).softDelete(eq(id), eq("tenant_dev"), any(Instant.class));
    }

    @Test
    void softDelete_missing_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, "tenant_dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.softDelete(id, "tenant_dev"))
                .isInstanceOf(NotificationWebhookNotFoundException.class);
    }

    @Test
    void restore_clearsDeletedAtAndEnables() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-07-31T09:00:00Z");
        Instant deleted = Instant.parse("2026-07-31T10:00:00Z");
        NotificationWebhookConfig deletedConfig = new NotificationWebhookConfig(
                id,
                "tenant_dev",
                "Claims",
                "https://example.com/hook",
                "enc:v1:x",
                false,
                deleted,
                created,
                deleted);
        when(repository.findByIdAndTenantIdIncludingDeleted(id, "tenant_dev"))
                .thenReturn(Optional.of(deletedConfig));
        when(repository.existsActiveByName("tenant_dev", "Claims", id)).thenReturn(false);

        NotificationWebhookConfig restored = useCase.restore(id, "tenant_dev");

        assertThat(restored.deletedAt()).isNull();
        assertThat(restored.enabled()).isTrue();
        ArgumentCaptor<NotificationWebhookConfig> saved =
                ArgumentCaptor.forClass(NotificationWebhookConfig.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(id);
        assertThat(saved.getValue().deletedAt()).isNull();
        assertThat(saved.getValue().enabled()).isTrue();
    }

    @Test
    void update_restoreFlag_delegatesToRestore() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-07-31T09:00:00Z");
        NotificationWebhookConfig deletedConfig = new NotificationWebhookConfig(
                id,
                "tenant_dev",
                "Claims",
                "https://example.com/hook",
                null,
                false,
                created,
                created,
                created);
        when(repository.findByIdAndTenantIdIncludingDeleted(id, "tenant_dev"))
                .thenReturn(Optional.of(deletedConfig));
        when(repository.existsActiveByName("tenant_dev", "Claims", id)).thenReturn(false);

        NotificationWebhookConfig restored = useCase.update(
                new ManageNotificationWebhookUseCase.UpdateCommand(
                        id, "tenant_dev", null, null, null, null, true));

        assertThat(restored.enabled()).isTrue();
        assertThat(restored.deletedAt()).isNull();
    }

    @Test
    void withMaskedSecret_masksOrNull() {
        Instant now = Instant.parse("2026-07-31T10:00:00Z");
        NotificationWebhookConfig withSecret = new NotificationWebhookConfig(
                UUID.randomUUID(),
                "tenant_dev",
                "Claims",
                "https://example.com/hook",
                "enc:v1:secret",
                true,
                null,
                now,
                now);
        assertThat(withSecret.withMaskedSecret().secretCiphertext())
                .isEqualTo(ManageNotificationWebhookUseCaseImpl.SECRET_MASK);

        NotificationWebhookConfig withoutSecret = new NotificationWebhookConfig(
                UUID.randomUUID(),
                "tenant_dev",
                "Claims",
                "https://example.com/hook",
                null,
                true,
                null,
                now,
                now);
        assertThat(withoutSecret.withMaskedSecret().secretCiphertext()).isNull();
    }
}
