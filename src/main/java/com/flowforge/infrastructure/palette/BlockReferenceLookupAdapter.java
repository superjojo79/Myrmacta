package com.flowforge.infrastructure.palette;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.TemplateStatus;
import com.flowforge.domain.document.port.out.DocumentTemplateRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.notification.port.out.NotificationWebhookRepository;
import com.flowforge.domain.workflow.palette.port.out.BlockReference;
import com.flowforge.domain.workflow.palette.port.out.BlockReferenceLookupPort;
import com.flowforge.domain.workflow.model.DecisionTableDeploymentInfo;
import com.flowforge.domain.workflow.port.out.DmnDeploymentPort;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * TODO §73 — lecture minimale des ressources du tenant référencées par les briques : uniquement {@code value} et
 * {@code label} (jamais configuration, secret ni contenu). Réutilise les dépôts existants.
 */
@Component
class BlockReferenceLookupAdapter implements BlockReferenceLookupPort {

    private final DocumentTemplateRepository templates;
    private final ConnectorInstanceRepository connectors;
    private final ConnectorCatalog connectorCatalog;
    private final NotificationWebhookRepository webhooks;
    private final DossierTypeRepository dossierTypes;
    private final WorkflowTypeConfigRepository workflowTypes;
    private final DmnDeploymentPort dmnDeployments;

    BlockReferenceLookupAdapter(
            DocumentTemplateRepository templates,
            ConnectorInstanceRepository connectors,
            ConnectorCatalog connectorCatalog,
            NotificationWebhookRepository webhooks,
            DossierTypeRepository dossierTypes,
            WorkflowTypeConfigRepository workflowTypes,
            DmnDeploymentPort dmnDeployments) {
        this.templates = templates;
        this.connectors = connectors;
        this.connectorCatalog = connectorCatalog;
        this.webhooks = webhooks;
        this.dossierTypes = dossierTypes;
        this.workflowTypes = workflowTypes;
        this.dmnDeployments = dmnDeployments;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BlockReference> list(String tenantId, String source) {
        return switch (source) {
            case "documentTemplates" -> activeTemplates(tenantId);
            case "connectorInstances" -> connectors.listByTenantId(tenantId).stream()
                    .filter(this::isUsableService)
                    .map(c -> new BlockReference(c.id().toString(), c.name()))
                    .toList();
            case "notificationWebhooks" -> webhooks.listByTenantId(tenantId).stream()
                    .filter(w -> w.enabled() && w.deletedAt() == null)
                    .map(w -> new BlockReference(w.id().toString(), w.name()))
                    .toList();
            case "dmnTables" -> dmnDeployments.findAllLatestDeployments(tenantId).stream()
                    .sorted(Comparator.comparing(DecisionTableDeploymentInfo::decisionKey))
                    .map(d -> new BlockReference(d.decisionKey(), d.decisionKey() + " (v" + d.version() + ")"))
                    .toList();
            default -> throw new IllegalArgumentException("Unsupported source: " + source);
        };
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(String tenantId, String source, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return switch (source) {
            case "documentTemplates" -> templates.listByTenantId(tenantId, value).stream()
                    .anyMatch(t -> t.status() == TemplateStatus.ACTIVE);
            case "connectorInstances" -> uuid(value)
                    .flatMap(id -> connectors.findByIdAndTenantId(id, tenantId))
                    .filter(this::isUsableService)
                    .isPresent();
            case "notificationWebhooks" -> uuid(value)
                    .flatMap(id -> webhooks.findByIdAndTenantId(id, tenantId))
                    .filter(w -> w.enabled() && w.deletedAt() == null)
                    .isPresent();
            case "dossierTypes" -> dossierTypes.findByCode(tenantId, value).filter(t -> t.active()).isPresent();
            case "workflowTypes" -> workflowTypes.findByWorkflowKey(tenantId, value).isPresent();
            case "dmnTables" -> dmnDeployments.findDeploymentInfo(tenantId, value).isPresent();
            default -> throw new IllegalArgumentException("Unsupported source: " + source);
        };
    }

    private List<BlockReference> activeTemplates(String tenantId) {
        Map<String, BlockReference> byKey = new LinkedHashMap<>();
        for (DocumentTemplate t : templates.listByTenantId(tenantId, null)) {
            if (t.status() == TemplateStatus.ACTIVE) {
                byKey.putIfAbsent(t.templateKey(), new BlockReference(t.templateKey(),
                        t.label() == null || t.label().isBlank() ? t.templateKey() : t.label()));
            }
        }
        return List.copyOf(byKey.values());
    }

    private boolean isUsableService(ConnectorInstanceData instance) {
        if (!instance.enabled()) {
            return false;
        }
        try {
            return connectorCatalog.get(instance.connectorType()).getCapability().includesService();
        } catch (RuntimeException unknownType) {
            return false;
        }
    }

    private static java.util.Optional<UUID> uuid(String value) {
        try {
            return java.util.Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return java.util.Optional.empty();
        }
    }
}
