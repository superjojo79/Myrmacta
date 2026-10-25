package be.myrmacta.infrastructure.palette;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.document.port.out.DocumentTemplateRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.notification.port.out.NotificationWebhookRepository;
import be.myrmacta.domain.workflow.palette.port.out.BlockReference;
import be.myrmacta.domain.workflow.palette.port.out.BlockReferenceLookupPort;
import be.myrmacta.domain.workflow.palette.port.out.ReferenceStatus;
import be.myrmacta.domain.workflow.model.DecisionTableDeploymentInfo;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
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
    public ReferenceStatus status(String tenantId, String source, String value) {
        if (value == null || value.isBlank()) {
            return ReferenceStatus.MISSING;
        }
        return switch (source) {
            case "documentTemplates" -> templateStatus(templates.listByTenantId(tenantId, value));
            case "connectorInstances" -> uuid(value)
                    .flatMap(id -> connectors.findByIdAndTenantId(id, tenantId))
                    .filter(this::isServiceType)
                    .map(c -> c.enabled() ? ReferenceStatus.ACTIVE : ReferenceStatus.INACTIVE)
                    .orElse(ReferenceStatus.MISSING);
            case "notificationWebhooks" -> uuid(value)
                    .flatMap(id -> webhooks.findByIdAndTenantId(id, tenantId))
                    .filter(w -> w.deletedAt() == null)
                    .map(w -> w.enabled() ? ReferenceStatus.ACTIVE : ReferenceStatus.INACTIVE)
                    .orElse(ReferenceStatus.MISSING);
            case "dossierTypes" -> dossierTypes.findByCode(tenantId, value)
                    .map(t -> t.active() ? ReferenceStatus.ACTIVE : ReferenceStatus.INACTIVE)
                    .orElse(ReferenceStatus.MISSING);
            case "workflowTypes" -> present(workflowTypes.findByWorkflowKey(tenantId, value).isPresent());
            case "dmnTables" -> present(dmnDeployments.findDeploymentInfo(tenantId, value).isPresent());
            default -> throw new IllegalArgumentException("Unsupported source: " + source);
        };
    }

    /** Une version active suffit ; un modèle qui n'a que des versions archivées est retiré (absent), pas « inactif ». */
    private static ReferenceStatus templateStatus(List<DocumentTemplate> versions) {
        if (versions.stream().anyMatch(t -> t.status() == TemplateStatus.ACTIVE)) {
            return ReferenceStatus.ACTIVE;
        }
        return versions.stream().anyMatch(t -> t.status() == TemplateStatus.DRAFT)
                ? ReferenceStatus.INACTIVE
                : ReferenceStatus.MISSING;
    }

    private static ReferenceStatus present(boolean present) {
        return present ? ReferenceStatus.ACTIVE : ReferenceStatus.MISSING;
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
        return instance.enabled() && isServiceType(instance);
    }

    private boolean isServiceType(ConnectorInstanceData instance) {
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
