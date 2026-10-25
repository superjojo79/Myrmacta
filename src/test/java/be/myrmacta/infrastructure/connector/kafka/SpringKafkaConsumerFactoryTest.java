package be.myrmacta.infrastructure.connector.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** No real broker (D-KAFKA-TEST) — only the pure consumer-properties construction is unit
 * tested; {@code start()} itself is exercised through {@code KafkaConsumerReconciler} against the
 * {@link KafkaConsumerFactory} fake. */
class SpringKafkaConsumerFactoryTest {

    @Test
    void plaintext_setsNoSaslProperties() {
        Map<String, Object> props = SpringKafkaConsumerFactory.buildConsumerProperties(Map.of(
                "bootstrapServers", "localhost:9092",
                "groupId", "myrmacta",
                "securityProtocol", "PLAINTEXT"));

        assertThat(props).containsEntry("security.protocol", "PLAINTEXT");
        assertThat(props).doesNotContainKeys("sasl.mechanism", "sasl.jaas.config");
    }

    @Test
    void saslSsl_setsJaasConfigFromCredentials() {
        Map<String, Object> props = SpringKafkaConsumerFactory.buildConsumerProperties(Map.of(
                "bootstrapServers", "localhost:9092",
                "groupId", "myrmacta",
                "securityProtocol", "SASL_SSL",
                "saslUsername", "svc",
                "saslPassword", "s3cr3t"));

        assertThat(props).containsEntry("security.protocol", "SASL_SSL");
        assertThat(props).containsEntry("sasl.mechanism", "PLAIN");
        assertThat(props.get("sasl.jaas.config").toString())
                .contains("username=\"svc\"")
                .contains("password=\"s3cr3t\"");
    }

    @Test
    void saslSsl_escapesQuotesAndBackslashesInCredentials() {
        // Revue adversariale (connecteurs-cible, lot B1) : un identifiant/mot de passe contenant
        // un guillemet ou un backslash ne doit pas casser la syntaxe du sasl.jaas.config généré.
        Map<String, Object> props = SpringKafkaConsumerFactory.buildConsumerProperties(Map.of(
                "bootstrapServers", "localhost:9092",
                "groupId", "myrmacta",
                "securityProtocol", "SASL_SSL",
                "saslUsername", "svc",
                "saslPassword", "a\"b\\c"));

        assertThat(props.get("sasl.jaas.config").toString()).contains("password=\"a\\\"b\\\\c\";");
    }

    @Test
    void bootstrapServersAndGroupId_arePropagated() {
        Map<String, Object> props = SpringKafkaConsumerFactory.buildConsumerProperties(Map.of(
                "bootstrapServers", "broker1:9092,broker2:9092",
                "groupId", "myrmacta-group",
                "securityProtocol", "PLAINTEXT"));

        assertThat(props)
                .containsEntry("bootstrap.servers", "broker1:9092,broker2:9092")
                .containsEntry("group.id", "myrmacta-group")
                .containsEntry("auto.offset.reset", "latest");
    }
}
