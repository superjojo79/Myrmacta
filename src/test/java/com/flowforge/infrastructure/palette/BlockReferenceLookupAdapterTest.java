package com.flowforge.infrastructure.palette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.TemplateStatus;
import com.flowforge.domain.document.port.out.DocumentTemplateRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.notification.model.NotificationWebhookConfig;
import com.flowforge.domain.notification.port.out.NotificationWebhookRepository;
import com.flowforge.domain.workflow.palette.port.out.BlockReference;
import com.flowforge.domain.workflow.palette.port.out.ReferenceStatus;
import com.flowforge.domain.workflow.model.DecisionTableDeploymentInfo;
import com.flowforge.domain.workflow.port.out.DmnDeploymentPort;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S-REF-3, S-REF-4, S-REFCHK-1/2 (côté adaptateur) — lecture minimale des ressources du tenant. */
class BlockReferenceLookupAdapterTest {

    private static final String TENANT = "tenant_a";
    private final DocumentTemplateRepository templates = mock(DocumentTemplateRepository.class);
    private final ConnectorInstanceRepository connectors = mock(ConnectorInstanceRepository.class);
    private final ConnectorCatalog catalog = mock(ConnectorCatalog.class);
    private final NotificationWebhookRepository webhooks = mock(NotificationWebhookRepository.class);
    private final DossierTypeRepository dossierTypes = mock(DossierTypeRepository.class);
    private final WorkflowTypeConfigRepository workflows = mock(WorkflowTypeConfigRepository.class);
    private final DmnDeploymentPort dmnDeployments = mock(DmnDeploymentPort.class);
    private final BlockReferenceLookupAdapter adapter = new BlockReferenceLookupAdapter(
            templates, connectors, catalog, webhooks, dossierTypes, workflows, dmnDeployments);

    @BeforeEach
    void setUp() {
        ConnectorDefinition service = mock(ConnectorDefinition.class);
        when(service.getCapability()).thenReturn(ConnectorCapability.SERVICE);
        ConnectorDefinition trigger = mock(ConnectorDefinition.class);
        when(trigger.getCapability()).thenReturn(ConnectorCapability.TRIGGER);
        when(catalog.get("http-rest")).thenReturn(service);
        when(catalog.get("kafka")).thenReturn(trigger);
    }

    private static DocumentTemplate template(String key, String label, TemplateStatus status) {
        Instant now = Instant.now();
        return new DocumentTemplate(UUID.randomUUID(), key, null, null, label, 1, status, "<p/>", TENANT, now, now, "u");
    }

    @Test
    void templatesListOnlyActiveKeysOncePerKey() {
        when(templates.listByTenantId(TENANT, null)).thenReturn(List.of(
                template("facture", "Facture", TemplateStatus.ACTIVE),
                template("facture", "Facture v2", TemplateStatus.ACTIVE),
                template("brouillon", "Brouillon", TemplateStatus.DRAFT)));

        assertThat(adapter.list(TENANT, "documentTemplates")).containsExactly(new BlockReference("facture", "Facture"));
    }

    @Test
    void connectorsListOnlyEnabledServiceInstances() {
        UUID ok = UUID.randomUUID();
        when(connectors.listByTenantId(TENANT)).thenReturn(List.of(
                new ConnectorInstanceData(ok, TENANT, "ERP", "http-rest", true, Map.of("token", "secret")),
                new ConnectorInstanceData(UUID.randomUUID(), TENANT, "Off", "http-rest", false, Map.of()),
                new ConnectorInstanceData(UUID.randomUUID(), TENANT, "Trigger", "kafka", true, Map.of()),
                new ConnectorInstanceData(UUID.randomUUID(), TENANT, "Inconnu", "gone", true, Map.of())));
        when(catalog.get("gone")).thenThrow(new IllegalArgumentException("unknown"));

        assertThat(adapter.list(TENANT, "connectorInstances")).containsExactly(new BlockReference(ok.toString(), "ERP"));
    }

    @Test
    void webhooksListOnlyEnabledNotDeleted() {
        UUID ok = UUID.randomUUID();
        Instant now = Instant.now();
        when(webhooks.listByTenantId(TENANT)).thenReturn(List.of(
                new NotificationWebhookConfig(ok, TENANT, "Slack", "https://x", "c", true, null, now, now),
                new NotificationWebhookConfig(UUID.randomUUID(), TENANT, "Off", "https://x", "c", false, null, now, now),
                new NotificationWebhookConfig(UUID.randomUUID(), TENANT, "Del", "https://x", "c", true, now, now, now)));

        assertThat(adapter.list(TENANT, "notificationWebhooks")).containsExactly(new BlockReference(ok.toString(), "Slack"));
    }

    @Test
    void dmnTablesListTheLatestDeploymentOfEachKeySortedByKeyWithItsVersionInTheLabel() {
        when(dmnDeployments.findAllLatestDeployments(TENANT)).thenReturn(List.of(
                new DecisionTableDeploymentInfo("scoring", 3), new DecisionTableDeploymentInfo("eligibilite", 1)));

        assertThat(adapter.list(TENANT, "dmnTables")).containsExactly(
                new BlockReference("eligibilite", "eligibilite (v1)"), new BlockReference("scoring", "scoring (v3)"));
    }

    @Test
    void dmnTablesAreReadForTheRequestedTenantOnly() {
        when(dmnDeployments.findAllLatestDeployments("tenant_b")).thenReturn(List.of());

        assertThat(adapter.list("tenant_b", "dmnTables")).isEmpty();
        org.mockito.Mockito.verify(dmnDeployments).findAllLatestDeployments("tenant_b");
        org.mockito.Mockito.verify(dmnDeployments, org.mockito.Mockito.never()).findAllLatestDeployments(TENANT);
    }

    @Test
    void dmnTableExistenceIsCheckedAgainstTheTenantDeployments() {
        when(dmnDeployments.findDeploymentInfo(TENANT, "scoring"))
                .thenReturn(Optional.of(new DecisionTableDeploymentInfo("scoring", 2)));
        when(dmnDeployments.findDeploymentInfo(TENANT, "absente")).thenReturn(Optional.empty());

        assertThat(adapter.exists(TENANT, "dmnTables", "scoring")).isTrue();
        assertThat(adapter.exists(TENANT, "dmnTables", "absente")).isFalse();
        assertThat(adapter.exists(TENANT, "dmnTables", " ")).isFalse();
        assertThat(adapter.exists("tenant_b", "dmnTables", "scoring")).isFalse();
    }

    @Test
    void existenceIsTenantScopedAndFormChecked() {
        UUID id = UUID.randomUUID();
        when(connectors.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.of(
                new ConnectorInstanceData(id, TENANT, "ERP", "http-rest", true, Map.of())));
        when(connectors.findByIdAndTenantId(any(), org.mockito.ArgumentMatchers.eq("tenant_b"))).thenReturn(Optional.empty());
        when(templates.listByTenantId(TENANT, "facture")).thenReturn(List.of(template("facture", "F", TemplateStatus.ACTIVE)));
        when(templates.listByTenantId(TENANT, "archive")).thenReturn(List.of(template("archive", "A", TemplateStatus.ARCHIVED)));
        var activeType = mock(com.flowforge.domain.dossier.model.DossierType.class);
        when(activeType.active()).thenReturn(true);
        when(dossierTypes.findByCode(TENANT, "SINISTRE")).thenReturn(Optional.of(activeType));
        // revue M3 : un type inactif ferait échouer la création du dossier enfant à l'exécution
        when(dossierTypes.findByCode(TENANT, "INACTIF")).thenReturn(Optional.of(mock(com.flowforge.domain.dossier.model.DossierType.class)));
        when(workflows.findByWorkflowKey(TENANT, "claim")).thenReturn(Optional.of(mock(com.flowforge.domain.workflow.model.WorkflowTypeConfig.class)));

        assertThat(adapter.exists(TENANT, "connectorInstances", id.toString())).isTrue();
        assertThat(adapter.exists("tenant_b", "connectorInstances", id.toString())).isFalse();
        assertThat(adapter.exists(TENANT, "connectorInstances", "pas-un-uuid")).isFalse();
        assertThat(adapter.exists(TENANT, "documentTemplates", "facture")).isTrue();
        assertThat(adapter.exists(TENANT, "documentTemplates", "archive")).isFalse();
        assertThat(adapter.exists(TENANT, "dossierTypes", "SINISTRE")).isTrue();
        assertThat(adapter.exists(TENANT, "dossierTypes", "AUTRE")).isFalse();
        assertThat(adapter.exists(TENANT, "dossierTypes", "INACTIF")).isFalse();
        assertThat(adapter.exists(TENANT, "workflowTypes", "claim")).isTrue();
        assertThat(adapter.exists(TENANT, "workflowTypes", " ")).isFalse();
    }

    /** TODO §92 D-DRAFT-REF-STATUS — un brouillon accepte une ressource qui existe mais n'est pas encore active. */
    @Test
    void statusDistinguishesActiveInactiveAndMissingResources() {
        UUID enabled = UUID.randomUUID();
        UUID disabled = UUID.randomUUID();
        UUID trigger = UUID.randomUUID();
        UUID unknownType = UUID.randomUUID();
        when(connectors.findByIdAndTenantId(enabled, TENANT)).thenReturn(Optional.of(
                new ConnectorInstanceData(enabled, TENANT, "ERP", "http-rest", true, Map.of())));
        when(connectors.findByIdAndTenantId(disabled, TENANT)).thenReturn(Optional.of(
                new ConnectorInstanceData(disabled, TENANT, "Off", "http-rest", false, Map.of())));
        when(connectors.findByIdAndTenantId(trigger, TENANT)).thenReturn(Optional.of(
                new ConnectorInstanceData(trigger, TENANT, "Trigger", "kafka", false, Map.of())));
        when(connectors.findByIdAndTenantId(unknownType, TENANT)).thenReturn(Optional.of(
                new ConnectorInstanceData(unknownType, TENANT, "Gone", "gone", false, Map.of())));
        when(catalog.get("gone")).thenThrow(new IllegalArgumentException("unknown"));
        when(connectors.findByIdAndTenantId(any(), org.mockito.ArgumentMatchers.eq("tenant_b"))).thenReturn(Optional.empty());

        assertThat(adapter.status(TENANT, "connectorInstances", enabled.toString())).isEqualTo(ReferenceStatus.ACTIVE);
        assertThat(adapter.status(TENANT, "connectorInstances", disabled.toString())).isEqualTo(ReferenceStatus.INACTIVE);
        assertThat(adapter.status(TENANT, "connectorInstances", trigger.toString())).isEqualTo(ReferenceStatus.MISSING);
        assertThat(adapter.status(TENANT, "connectorInstances", unknownType.toString())).isEqualTo(ReferenceStatus.MISSING);
        assertThat(adapter.status("tenant_b", "connectorInstances", disabled.toString())).isEqualTo(ReferenceStatus.MISSING);
        assertThat(adapter.status(TENANT, "connectorInstances", "pas-un-uuid")).isEqualTo(ReferenceStatus.MISSING);
    }

    @Test
    void templateStatusIsActiveWithAnActiveVersionInactiveWithOnlyDraftsAndMissingOtherwise() {
        when(templates.listByTenantId(TENANT, "facture")).thenReturn(List.of(
                template("facture", "F", TemplateStatus.DRAFT), template("facture", "F", TemplateStatus.ACTIVE)));
        when(templates.listByTenantId(TENANT, "nouveau")).thenReturn(List.of(template("nouveau", "N", TemplateStatus.DRAFT)));
        // Un modèle seulement archivé est retiré : un brouillon ne doit pas le ressusciter.
        when(templates.listByTenantId(TENANT, "archive")).thenReturn(List.of(template("archive", "A", TemplateStatus.ARCHIVED)));
        when(templates.listByTenantId(TENANT, "absent")).thenReturn(List.of());

        assertThat(adapter.status(TENANT, "documentTemplates", "facture")).isEqualTo(ReferenceStatus.ACTIVE);
        assertThat(adapter.status(TENANT, "documentTemplates", "nouveau")).isEqualTo(ReferenceStatus.INACTIVE);
        assertThat(adapter.status(TENANT, "documentTemplates", "archive")).isEqualTo(ReferenceStatus.MISSING);
        assertThat(adapter.status(TENANT, "documentTemplates", "absent")).isEqualTo(ReferenceStatus.MISSING);
        assertThat(adapter.exists(TENANT, "documentTemplates", "nouveau")).isFalse();
    }

    @Test
    void webhookAndDossierTypeStatusFollowEnabledAndActiveFlags() {
        Instant now = Instant.now();
        UUID on = UUID.randomUUID();
        UUID off = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        when(webhooks.findByIdAndTenantId(on, TENANT)).thenReturn(Optional.of(
                new NotificationWebhookConfig(on, TENANT, "Slack", "https://x", "c", true, null, now, now)));
        when(webhooks.findByIdAndTenantId(off, TENANT)).thenReturn(Optional.of(
                new NotificationWebhookConfig(off, TENANT, "Off", "https://x", "c", false, null, now, now)));
        when(webhooks.findByIdAndTenantId(deleted, TENANT)).thenReturn(Optional.of(
                new NotificationWebhookConfig(deleted, TENANT, "Del", "https://x", "c", false, now, now, now)));
        var activeType = mock(com.flowforge.domain.dossier.model.DossierType.class);
        when(activeType.active()).thenReturn(true);
        when(dossierTypes.findByCode(TENANT, "SINISTRE")).thenReturn(Optional.of(activeType));
        when(dossierTypes.findByCode(TENANT, "INACTIF")).thenReturn(Optional.of(mock(com.flowforge.domain.dossier.model.DossierType.class)));

        assertThat(adapter.status(TENANT, "notificationWebhooks", on.toString())).isEqualTo(ReferenceStatus.ACTIVE);
        assertThat(adapter.status(TENANT, "notificationWebhooks", off.toString())).isEqualTo(ReferenceStatus.INACTIVE);
        assertThat(adapter.status(TENANT, "notificationWebhooks", deleted.toString())).isEqualTo(ReferenceStatus.MISSING);
        assertThat(adapter.status(TENANT, "dossierTypes", "SINISTRE")).isEqualTo(ReferenceStatus.ACTIVE);
        assertThat(adapter.status(TENANT, "dossierTypes", "INACTIF")).isEqualTo(ReferenceStatus.INACTIVE);
        assertThat(adapter.status(TENANT, "dossierTypes", "AUTRE")).isEqualTo(ReferenceStatus.MISSING);
        assertThat(adapter.status(TENANT, "dossierTypes", " ")).isEqualTo(ReferenceStatus.MISSING);
    }

    @Test
    void workflowTypesAndDmnTablesHaveNoInactiveState() {
        when(workflows.findByWorkflowKey(TENANT, "claim")).thenReturn(Optional.of(mock(com.flowforge.domain.workflow.model.WorkflowTypeConfig.class)));
        when(dmnDeployments.findDeploymentInfo(TENANT, "scoring")).thenReturn(Optional.of(new DecisionTableDeploymentInfo("scoring", 1)));

        assertThat(adapter.status(TENANT, "workflowTypes", "claim")).isEqualTo(ReferenceStatus.ACTIVE);
        assertThat(adapter.status(TENANT, "workflowTypes", "autre")).isEqualTo(ReferenceStatus.MISSING);
        assertThat(adapter.status(TENANT, "dmnTables", "scoring")).isEqualTo(ReferenceStatus.ACTIVE);
        assertThat(adapter.status(TENANT, "dmnTables", "absente")).isEqualTo(ReferenceStatus.MISSING);
    }
}
