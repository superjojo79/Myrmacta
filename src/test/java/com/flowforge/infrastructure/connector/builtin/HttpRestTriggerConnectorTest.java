package com.flowforge.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.model.WebhookAuthType;
import com.flowforge.domain.connector.model.WorkflowTriggerEvent;
import com.flowforge.infrastructure.connector.mapping.SimpleJsonPathMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HttpRestTriggerConnectorTest {

    private final HttpRestTriggerConnector connector = new HttpRestTriggerConnector(new SimpleJsonPathMapper());

    @Test
    void mapsObjectPayload_toSingleEvent() {
        var instance = instance(Map.of(
                "processKey", "smokeProcess",
                "dossierTypeCode", "SINISTRE",
                "webhookAuthType", WebhookAuthType.HMAC_SHA256.name(),
                "webhookSecret", "s",
                "mapping", Map.of(
                        "fields", Map.of("claimNumber", "$.claim.id"),
                        "variables", Map.of("source", "$.meta.source"))));

        List<WorkflowTriggerEvent> events = connector.onEvent(
                instance,
                Map.of(
                        "claim", Map.of("id", "C-1"),
                        "meta", Map.of("source", "partner")));

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().processKey()).isEqualTo("smokeProcess");
        assertThat(events.getFirst().dossierTypeCode()).isEqualTo("SINISTRE");
        assertThat(events.getFirst().dossierFields()).containsEntry("claimNumber", "C-1");
        assertThat(events.getFirst().variables()).containsEntry("source", "partner");
    }

    @Test
    void mapsArrayPayload_toMultipleEvents() {
        var instance = instance(Map.of(
                "processKey", "smokeProcess",
                "dossierTypeCode", "SINISTRE",
                "webhookAuthType", "HMAC_SHA256",
                "webhookSecret", "s",
                "mapping", Map.of(
                        "itemsPath", "$",
                        "fields", Map.of("claimNumber", "$.id"))));

        List<WorkflowTriggerEvent> events = connector.onEvent(
                instance, List.of(Map.of("id", "1"), Map.of("id", "2")));

        assertThat(events).hasSize(2);
        assertThat(events.get(0).dossierFields()).containsEntry("claimNumber", "1");
        assertThat(events.get(1).dossierFields()).containsEntry("claimNumber", "2");
    }

    @Test
    void existingDossierReferencePath_setsReference() {
        var instance = instance(Map.of(
                "processKey", "smokeProcess",
                "dossierTypeCode", "SINISTRE",
                "webhookAuthType", "HMAC_SHA256",
                "webhookSecret", "s",
                "mapping", Map.of("existingDossierReferencePath", "$.dossierRef")));

        var events = connector.onEvent(instance, Map.of("dossierRef", "DOS-1"));
        assertThat(events.getFirst().existingDossierReference()).isEqualTo("DOS-1");
    }

    @Test
    void processKeyPath_overridesDefault() {
        var instance = instance(Map.of(
                "processKey", "defaultProcess",
                "dossierTypeCode", "SINISTRE",
                "webhookAuthType", "HMAC_SHA256",
                "webhookSecret", "s",
                "mapping", Map.of("processKeyPath", "$.processKey")));

        var events = connector.onEvent(instance, Map.of("processKey", "otherProcess"));
        assertThat(events.getFirst().processKey()).isEqualTo("otherProcess");
    }

    @Test
    void validate_rejectsIpAllowlist() {
        assertThatThrownBy(() -> connector.validate(Map.of(
                        "webhookAuthType", "IP_ALLOWLIST",
                        "webhookSecret", "s",
                        "processKey", "p",
                        "dossierTypeCode", "T")))
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("IP_ALLOWLIST");
    }

    private static ConnectorInstanceData instance(Map<String, Object> config) {
        return new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "http", "http-rest", true, new HashMap<>(config));
    }
}
