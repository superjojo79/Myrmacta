package com.flowforge.domain.notification.usecase;

import com.flowforge.domain.notification.model.NotificationValidationException;
import com.flowforge.domain.notification.model.NotificationWebhookConflictException;
import com.flowforge.domain.notification.model.NotificationWebhookConfig;
import com.flowforge.domain.notification.model.NotificationWebhookNotFoundException;
import com.flowforge.domain.notification.port.in.ManageNotificationWebhookUseCase;
import com.flowforge.domain.notification.port.out.NotificationWebhookRepository;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Admin CRUD for notification webhook configs. URL SSRF checks and secret encryption
 * are performed by infrastructure adapters around this use case.
 */
public final class ManageNotificationWebhookUseCaseImpl implements ManageNotificationWebhookUseCase {

    /** Secret mask returned by the API; ignored on write. */
    public static final String SECRET_MASK = NotificationWebhookConfig.SECRET_MASK;

    private final NotificationWebhookRepository repository;

    public ManageNotificationWebhookUseCaseImpl(NotificationWebhookRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public NotificationWebhookConfig create(CreateCommand command) {
        Objects.requireNonNull(command, "command");
        String name = requireNonBlank(command.name(), "name");
        String targetUrl = requireNonBlank(command.targetUrl(), "targetUrl");
        String tenantId = requireNonBlank(command.tenantId(), "tenantId");
        ensureUniqueName(tenantId, name, null);

        Instant now = Instant.now();
        NotificationWebhookConfig config = new NotificationWebhookConfig(
                UUID.randomUUID(),
                tenantId,
                name,
                targetUrl,
                blankToNull(command.secret()),
                command.enabled(),
                null,
                now,
                now);
        return repository.save(config);
    }

    @Override
    public NotificationWebhookConfig get(UUID id, String tenantId) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotificationWebhookNotFoundException(id));
    }

    @Override
    public PageResult<NotificationWebhookConfig> list(String tenantId, PageQuery query) {
        return repository.pageByTenantId(tenantId, query);
    }

    @Override
    public NotificationWebhookConfig update(UpdateCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.restore()) {
            return restore(command.id(), command.tenantId());
        }

        NotificationWebhookConfig existing = repository
                .findByIdAndTenantId(command.id(), command.tenantId())
                .orElseThrow(() -> new NotificationWebhookNotFoundException(command.id()));

        String name = command.name() == null || command.name().isBlank()
                ? existing.name()
                : command.name().trim();
        String targetUrl = command.targetUrl() == null || command.targetUrl().isBlank()
                ? existing.targetUrl()
                : command.targetUrl().trim();
        boolean enabled = command.enabled() == null ? existing.enabled() : command.enabled();
        String secret = isBlankOrMask(command.secret())
                ? existing.secretCiphertext()
                : command.secret();

        if (!name.equals(existing.name())) {
            ensureUniqueName(command.tenantId(), name, command.id());
        }

        Instant now = Instant.now();
        return repository.save(new NotificationWebhookConfig(
                existing.id(),
                existing.tenantId(),
                name,
                targetUrl,
                secret,
                enabled,
                null,
                existing.createdAt(),
                now));
    }

    @Override
    public void softDelete(UUID id, String tenantId) {
        repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotificationWebhookNotFoundException(id));
        repository.softDelete(id, tenantId, Instant.now());
    }

    @Override
    public NotificationWebhookConfig restore(UUID id, String tenantId) {
        NotificationWebhookConfig existing = repository
                .findByIdAndTenantIdIncludingDeleted(id, tenantId)
                .orElseThrow(() -> new NotificationWebhookNotFoundException(id));
        ensureUniqueName(tenantId, existing.name(), id);

        Instant now = Instant.now();
        return repository.save(new NotificationWebhookConfig(
                existing.id(),
                existing.tenantId(),
                existing.name(),
                existing.targetUrl(),
                existing.secretCiphertext(),
                true,
                null,
                existing.createdAt(),
                now));
    }

    private void ensureUniqueName(String tenantId, String name, UUID excludeId) {
        if (repository.existsActiveByName(tenantId, name, excludeId)) {
            throw new NotificationWebhookConflictException(
                    "Notification webhook name already exists: " + name);
        }
    }

    private static boolean isBlankOrMask(String secret) {
        return secret == null || secret.isBlank() || SECRET_MASK.equals(secret);
    }

    private static String blankToNull(String secret) {
        return secret == null || secret.isBlank() ? null : secret;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new NotificationValidationException(field + " is required");
        }
        return value.trim();
    }
}
