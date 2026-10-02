package com.flowforge.domain.connector.usecase;

import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorInstanceNotFoundException;
import com.flowforge.domain.connector.model.ConnectorInstanceQuotaExceededException;
import com.flowforge.domain.connector.model.ConnectorValidationException;
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
    private final Map<String, Integer> maxEnabledInstancesPerTenantByType;

    public ManageConnectorInstanceUseCaseImpl(
            ConnectorInstanceRepository repository, ConnectorCatalog catalog, TenantAdminGuard tenantAdminGuard) {
        this(repository, catalog, tenantAdminGuard, Map.of());
    }

    /**
     * @param maxEnabledInstancesPerTenantByType per-type ceiling on how many <em>enabled</em>
     *     instances a tenant may have at once, keyed by {@code connectorType} — a type absent
     *     from this map has no limit. Kept generic (no connector type ever hardcoded here, same
     *     invariant as the rest of this class) — a specific type's limit is a wiring/config
     *     concern for infrastructure, not this domain use case (found in adversarial review,
     *     connecteurs-cible lot B1: an unbounded number of enabled {@code kafka} instances each
     *     run a real consumer thread for the JVM's lifetime).
     */
    public ManageConnectorInstanceUseCaseImpl(
            ConnectorInstanceRepository repository,
            ConnectorCatalog catalog,
            TenantAdminGuard tenantAdminGuard,
            Map<String, Integer> maxEnabledInstancesPerTenantByType) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.tenantAdminGuard = Objects.requireNonNull(tenantAdminGuard, "tenantAdminGuard");
        this.maxEnabledInstancesPerTenantByType =
                Map.copyOf(Objects.requireNonNull(maxEnabledInstancesPerTenantByType, "maxEnabledInstancesPerTenantByType"));
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
        assertNoMaskedSecret(command.connectorType(), config);
        definition.validate(config);
        assertKnownConfigKeys(definition, config);
        if (command.enabled()) {
            assertWithinQuota(command.tenantId(), command.connectorType(), null);
        }
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
        assertKnownConfigKeys(definition, merged);
        if (enabled && !existing.enabled()) {
            assertWithinQuota(existing.tenantId(), existing.connectorType(), existing.id());
        }

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
        assertWithinQuota(existing.tenantId(), existing.connectorType(), existing.id());
        return repository.save(new ConnectorInstanceData(
                existing.id(),
                existing.tenantId(),
                existing.name(),
                existing.connectorType(),
                true,
                existing.config()));
    }

    /**
     * @throws ConnectorInstanceQuotaExceededException if enabling one more instance of {@code
     *     connectorType} would push the tenant's count of <em>enabled</em> instances of that type
     *     past its configured limit (a type absent from {@code maxEnabledInstancesPerTenantByType}
     *     has no limit). {@code excludingInstanceId} lets an already-counted instance (an update
     *     or a restore) recheck the limit without double-counting itself.
     */
    private void assertWithinQuota(String tenantId, String connectorType, UUID excludingInstanceId) {
        Integer max = maxEnabledInstancesPerTenantByType.get(connectorType);
        if (max == null) {
            return;
        }
        long enabledCount = repository.listByTenantId(tenantId).stream()
                .filter(instance -> connectorType.equals(instance.connectorType()))
                .filter(ConnectorInstanceData::enabled)
                .filter(instance -> !instance.id().equals(excludingInstanceId))
                .count();
        if (enabledCount >= max) {
            throw new ConnectorInstanceQuotaExceededException(
                    "Tenant already has " + enabledCount + " enabled '" + connectorType
                            + "' instance(s), the configured limit is " + max);
        }
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

    /** Un masque recopié depuis une lecture ne doit jamais devenir un vrai secret à la création. */
    private void assertNoMaskedSecret(String connectorType, Map<String, Object> config) {
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            boolean isSecret = isSecretKey(entry.getKey()) || catalog.isPasswordField(connectorType, entry.getKey());
            if (isSecret && entry.getValue() != null && SECRET_MASK.equals(String.valueOf(entry.getValue()))) {
                throw new com.flowforge.domain.connector.model.ConnectorValidationException(
                        entry.getKey() + " cannot be the masked placeholder; provide the actual value or omit it");
            }
        }
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

    private static void assertKnownConfigKeys(ConnectorDefinition definition, Map<String, Object> config) {
        List<ConfigField> schema = definition.getConfigSchema();
        if (schema == null) {
            schema = List.of();
        }
        java.util.Set<String> allowed = new java.util.HashSet<>();
        for (ConfigField field : schema) {
            allowed.add(field.name());
        }
        for (String key : config.keySet()) {
            if (!allowed.contains(key)) {
                throw new ConnectorValidationException("Unknown config key: " + key);
            }
        }
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        return name;
    }
}
