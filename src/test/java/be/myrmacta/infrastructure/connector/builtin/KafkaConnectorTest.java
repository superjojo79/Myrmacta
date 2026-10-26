package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KafkaConnectorTest {

    private KafkaConnector connector;

    @BeforeEach
    void setUp() {
        connector = new KafkaConnector(new SimpleJsonPathMapper());
    }

    @Test
    void capability_isTriggerOnly() {
        assertThat(connector.getCapability()).isEqualTo(ConnectorCapability.TRIGGER);
    }

    @Test
    void validate_missingRequiredFields_throws() {
        assertThatThrownBy(() -> connector.validate(Map.of()))
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Test
    void validate_plaintext_withoutSaslCredentials_passes() {
        Map<String, Object> config = baseConfig();
        config.put("securityProtocol", "PLAINTEXT");

        connector.validate(config);
    }

    @Test
    void validate_saslSsl_withoutCredentials_throws() {
        Map<String, Object> config = baseConfig();
        config.put("securityProtocol", "SASL_SSL");

        assertThatThrownBy(() -> connector.validate(config)).isInstanceOf(ConnectorValidationException.class);
    }

    @Test
    void validate_saslSsl_withCredentials_passes() {
        Map<String, Object> config = baseConfig();
        config.put("securityProtocol", "SASL_SSL");
        config.put("saslUsername", "svc");
        config.put("saslPassword", "secret12345678901234");

        connector.validate(config);
    }

    @Test
    void validate_unsupportedSecurityProtocol_throws() {
        Map<String, Object> config = baseConfig();
        config.put("securityProtocol", "SSL");

        assertThatThrownBy(() -> connector.validate(config)).isInstanceOf(ConnectorValidationException.class);
    }

    @Test
    void onEvent_mapsMessage_toSingleEvent() {
        var instance = instance(Map.of(
                "processKey", "smokeProcess",
                "dossierTypeCode", "SINISTRE",
                "mapping", Map.of(
                        "fields", Map.of("claimNumber", "$.claim.id"),
                        "variables", Map.of("source", "$.meta.source"))));

        List<WorkflowTriggerEvent> events = connector.onEvent(
                instance,
                Map.of("claim", Map.of("id", "C-1"), "meta", Map.of("source", "core")));

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().processKey()).isEqualTo("smokeProcess");
        assertThat(events.getFirst().dossierTypeCode()).isEqualTo("SINISTRE");
        assertThat(events.getFirst().dossierFields()).containsEntry("claimNumber", "C-1");
        assertThat(events.getFirst().variables()).containsEntry("source", "core");
    }

    @Test
    void onEvent_existingDossierReference_targetsExistingDossier() {
        var instance = instance(Map.of(
                "processKey", "smokeProcess",
                "dossierTypeCode", "SINISTRE",
                "mapping", Map.of("existingDossierReferencePath", "$.ref")));

        List<WorkflowTriggerEvent> events = connector.onEvent(instance, Map.of("ref", "DOS-1"));

        assertThat(events.getFirst().existingDossierReference()).isEqualTo("DOS-1");
    }

    @Test
    void onEvent_processKeyOverridePath_takesPrecedenceOverDefault() {
        var instance = instance(Map.of(
                "processKey", "defaultProcess",
                "dossierTypeCode", "SINISTRE",
                "mapping", Map.of("processKeyPath", "$.pk")));

        List<WorkflowTriggerEvent> events = connector.onEvent(instance, Map.of("pk", "overrideProcess"));

        assertThat(events.getFirst().processKey()).isEqualTo("overrideProcess");
    }

    @Test
    void onEvent_missingResolvableProcessKey_throws() {
        // No instance-level processKey and no mapping override resolving to a value.
        var instance = new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "k", "kafka", true,
                Map.of("dossierTypeCode", "SINISTRE", "mapping", Map.of("processKeyPath", "$.pk")));

        assertThatThrownBy(() -> connector.onEvent(instance, Map.of()))
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Test
    void onEvent_blankMappedField_throwsConnectorValidationException() {
        // Même correction que BatchImportConnector : un champ mappé (mapping.fields) résolvant à
        // null/blanc est un message "malformé" — pas processKey/dossierTypeCode, qui ont toujours
        // un repli d'instance obligatoire (validate()).
        var instance = instance(Map.of(
                "processKey", "p", "dossierTypeCode", "T",
                "mapping", Map.of("fields", Map.of("claimNumber", "$.claim.id"))));

        assertThatThrownBy(() -> connector.onEvent(instance, Map.of("claim", Map.of())))
                .isInstanceOf(ConnectorValidationException.class);
    }

    private static Map<String, Object> baseConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("bootstrapServers", "localhost:9092");
        config.put("topic", "claims.events");
        config.put("groupId", "myrmacta-batch");
        config.put("securityProtocol", "PLAINTEXT");
        config.put("processKey", "importProcess");
        config.put("dossierTypeCode", "IMPORT");
        return config;
    }

    private static ConnectorInstanceData instance(Map<String, Object> config) {
        Map<String, Object> full = new LinkedHashMap<>(baseConfig());
        full.putAll(config);
        return new ConnectorInstanceData(UUID.randomUUID(), "tenant_dev", "kafka", "kafka", true, full);
    }
}
