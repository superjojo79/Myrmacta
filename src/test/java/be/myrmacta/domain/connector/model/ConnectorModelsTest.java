package be.myrmacta.domain.connector.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectorModelsTest {

    @Test
    void connectorInstanceData_requiresIdTenantNameAndType() {
        UUID id = UUID.randomUUID();
        var data = new ConnectorInstanceData(
                id, "tenant_dev", "Claims inbound", "http-rest", true, Map.of("webhookAuthType", "HMAC_SHA256"));

        assertThat(data.id()).isEqualTo(id);
        assertThat(data.tenantId()).isEqualTo("tenant_dev");
        assertThat(data.name()).isEqualTo("Claims inbound");
        assertThat(data.connectorType()).isEqualTo("http-rest");
        assertThat(data.enabled()).isTrue();
        assertThat(data.config()).containsEntry("webhookAuthType", "HMAC_SHA256");
    }

    @Test
    void connectorInstanceData_nullConfig_becomesEmptyMap() {
        var data = new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "n", "http-rest", false, null);
        assertThat(data.config()).isEmpty();
    }

    @Test
    void connectorInstanceData_nullName_rejected() {
        assertThatThrownBy(() -> new ConnectorInstanceData(
                        UUID.randomUUID(), "tenant_dev", null, "http-rest", true, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name");
    }

    @Test
    void webhookAuthType_supportsHmacAndSharedSecretOnly() {
        assertThat(WebhookAuthType.HMAC_SHA256.name()).isEqualTo("HMAC_SHA256");
        assertThat(WebhookAuthType.SHARED_SECRET_TOKEN.name()).isEqualTo("SHARED_SECRET_TOKEN");
        assertThat(WebhookAuthType.values()).hasSize(2);
    }

    @Test
    void webhookAuthType_rejectsIpAllowlist() {
        assertThatThrownBy(() -> WebhookAuthType.valueOf("IP_ALLOWLIST"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void workflowTriggerEvent_copiesMapsAndAllowsExistingReference() {
        var event = new WorkflowTriggerEvent(
                "smokeProcess",
                "SINISTRE",
                Map.of("claimNumber", "42"),
                Map.of("source", "webhook"),
                "DOS-SINISTRE-2024-000042");

        assertThat(event.processKey()).isEqualTo("smokeProcess");
        assertThat(event.dossierTypeCode()).isEqualTo("SINISTRE");
        assertThat(event.dossierFields()).containsEntry("claimNumber", "42");
        assertThat(event.variables()).containsEntry("source", "webhook");
        assertThat(event.existingDossierReference()).isEqualTo("DOS-SINISTRE-2024-000042");
    }

    @Test
    void workflowTriggerEvent_nullMaps_becomeEmpty() {
        var event = new WorkflowTriggerEvent("p", "T", null, null, null);
        assertThat(event.dossierFields()).isEmpty();
        assertThat(event.variables()).isEmpty();
        assertThat(event.existingDossierReference()).isNull();
    }

    @Test
    void workflowTriggerEvent_requiresProcessKey() {
        assertThatThrownBy(() -> new WorkflowTriggerEvent(null, "T", Map.of(), Map.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("processKey");
    }

    @Test
    void configField_passwordType_andOptionsCopied() {
        var field = new ConfigField(
                "webhookSecret",
                "Webhook secret",
                ConfigFieldType.PASSWORD,
                true,
                null,
                List.of(),
                "HMAC shared secret");

        assertThat(field.type()).isEqualTo(ConfigFieldType.PASSWORD);
        assertThat(field.required()).isTrue();
        assertThat(field.options()).isEmpty();
    }

    @Test
    void configField_nullOptions_becomeEmpty() {
        var field = new ConfigField("x", "X", ConfigFieldType.STRING, false, "d", null, null);
        assertThat(field.options()).isEmpty();
        assertThat(field.defaultValue()).isEqualTo("d");
    }

    @Test
    void connectorCapability_includesTriggerServiceBoth() {
        assertThat(ConnectorCapability.TRIGGER).isNotNull();
        assertThat(ConnectorCapability.SERVICE).isNotNull();
        assertThat(ConnectorCapability.BOTH).isNotNull();
    }

    @Test
    void connectorCapability_both_impliesTriggerAndService() {
        assertThat(ConnectorCapability.BOTH.includesTrigger()).isTrue();
        assertThat(ConnectorCapability.BOTH.includesService()).isTrue();
        assertThat(ConnectorCapability.TRIGGER.includesTrigger()).isTrue();
        assertThat(ConnectorCapability.TRIGGER.includesService()).isFalse();
        assertThat(ConnectorCapability.SERVICE.includesTrigger()).isFalse();
        assertThat(ConnectorCapability.SERVICE.includesService()).isTrue();
    }
}
