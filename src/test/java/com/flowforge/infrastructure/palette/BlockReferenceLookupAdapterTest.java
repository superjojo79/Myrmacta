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
    private final BlockReferenceLookupAdapter adapter =
            new BlockReferenceLookupAdapter(templates, connectors, catalog, webhooks, dossierTypes, workflows);

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
}
