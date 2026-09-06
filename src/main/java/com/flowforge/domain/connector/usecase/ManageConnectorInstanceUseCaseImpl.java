package com.flowforge.domain.connector.usecase;

import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorInstanceNotFoundException;
import com.flowforge.domain.connector.port.in.ManageConnectorInstanceUseCase;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Gestion CRUD des instances de connecteur (D-ADMIN, DD-SOFT, DD-NAME-UQ).
 * POJO; no framework dependency (ArchUnit). Secret encryption is handled
 * transparently by the persistence adapter. {@code TenantAdminGuard} (shared beyond the
 * {@code dossier} bounded context, extension-mcp-flux-metier D2) reproduces on every method the
 * {@code TENANT_ADMIN}-only {@code @PreAuthorize} already at the class level of
 * {@code ConnectorAdminController} — otherwise an MCP caller invoking this use case directly
 * would bypass that REST-only check entirely.
 */
public final class ManageConnectorInstanceUseCaseImpl implements ManageConnectorInstanceUseCase {

    /** Secret mask returned by the API; ignored on write. */
    public static final String SECRET_MASK = "********";

    private final ConnectorInstanceRepository repository;
    private final ConnectorCatalog catalog;
    private final TenantAdminGuard tenantAdminGuard;

    public ManageConnectorInstanceUseCaseImpl(
            ConnectorInstanceRepository repository, ConnectorCatalog catalog, TenantAdminGuard tenantAdminGuard) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.tenantAdminGuard = Objects.requireNonNull(tenantAdminGuard, "tenantAdminGuard");
    }

    @Override
    public void assertAuthorized(AccessContext.User actor) {
        tenantAdminGuard.requireTenantAdminOrApiService(actor);
    }

    @Override
    public ConnectorInstanceData create(CreateCommand command, AccessContext.User actor) {
        tenantAdminGuard.requireTenantAdminOrApiService(actor);
        Objects.requireNonNull(command, "command");
        ConnectorDefinition definition = catalog.get(command.connectorType());
        Map<String, Object> config = command.config() == null ? Map.of() : command.config();
        definition.validate(config);
        ConnectorInstanceData data = new ConnectorInstanceData(
                UUID.randomUUID(),
                command.tenantId(),
                requireName(command.name()),
                command.connectorType(),
                command.enabled(),
                config);
        return repository.save(data);
    }

    @Override
    public ConnectorInstanceData update(UpdateCommand command, AccessContext.User actor) {
        tenantAdminGuard.requireTenantAdminOrApiService(actor);
        Objects.requireNonNull(command, "command");
        if (command.restore()) {
            return restore(command.id(), command.tenantId(), actor);
        }
        ConnectorInstanceData existing = repository
                .findByIdAndTenantId(command.id(), command.tenantId())
                .orElseThrow(() -> new ConnectorInstanceNotFoundException(command.id()));

        String name = command.name() == null || command.name().isBlank()
                ? existing.name()
                : command.name();
        boolean enabled = command.enabled() == null ? existing.enabled() : command.enabled();
        Map<String, Object> merged = mergeConfig(existing.connectorType(), existing.config(), command.config());

        ConnectorDefinition definition = catalog.get(existing.connectorType());
        definition.validate(merged);

        return repository.save(new ConnectorInstanceData(
                existing.id(),
                existing.tenantId(),
                name,
                existing.connectorType(),
                enabled,
                merged));
    }

    @Override
    public ConnectorInstanceData get(UUID id, String tenantId, AccessContext.User actor) {
        tenantAdminGuard.requireTenantAdminOrApiService(actor);
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ConnectorInstanceNotFoundException(id));
    }

    @Override
    public PageResult<ConnectorInstanceData> list(String tenantId, PageQuery query, AccessContext.User actor) {
        tenantAdminGuard.requireTenantAdminOrApiService(actor);
        return repository.pageByTenantId(tenantId, query);
    }

    @Override
    public void softDelete(UUID id, String tenantId, AccessContext.User actor) {
        tenantAdminGuard.requireTenantAdminOrApiService(actor);
        repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ConnectorInstanceNotFoundException(id));
        repository.softDelete(id, tenantId);
    }

    @Override
    public ConnectorInstanceData restore(UUID id, String tenantId, AccessContext.User actor) {
        tenantAdminGuard.requireTenantAdminOrApiService(actor);
        ConnectorInstanceData existing = repository
                .findByIdAndTenantIdIncludingDeleted(id, tenantId)
                .orElseThrow(() -> new ConnectorInstanceNotFoundException(id));
        return repository.save(new ConnectorInstanceData(
                existing.id(),
                existing.tenantId(),
                existing.name(),
                existing.connectorType(),
                true,
                existing.config()));
    }

    private Map<String, Object> mergeConfig(
            String connectorType, Map<String, Object> existing, Map<String, Object> updates) {
        Map<String, Object> merged = new LinkedHashMap<>(existing == null ? Map.of() : existing);
        if (updates == null) {
            return merged;
        }
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            boolean isSecret = isSecretKey(entry.getKey()) || catalog.isPasswordField(connectorType, entry.getKey());
            if (isSecret && isBlankOrMask(entry.getValue())) {
                // Preserve the current secret when clients submit the mask or a blank value.
                continue;
            }
            merged.put(entry.getKey(), entry.getValue());
        }
        return merged;
    }

    private static boolean isBlankOrMask(Object value) {
        if (value == null) {
            return true;
        }
        String text = String.valueOf(value);
        return text.isBlank() || SECRET_MASK.equals(text);
    }

    public static boolean isSecretKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.equals("password")
                || k.endsWith("secret")
                || k.endsWith("password");
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        return name;
    }
}
