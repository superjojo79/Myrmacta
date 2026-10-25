package be.myrmacta.infrastructure.connector.kafka;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Real adapter for {@link KafkaConsumerFactory} — Spring Kafka {@link KafkaMessageListenerContainer},
 * one per connector instance (D-KAFKA-LIFECYCLE, design). API verified against the resolved
 * spring-kafka 4.1.1 jar (javap), not assumed, same discipline as the SSHJ adapter (T1.10).
 */
@Component
class SpringKafkaConsumerFactory implements KafkaConsumerFactory {

    @Override
    public KafkaConsumerHandle start(ConnectorInstanceData instance, Consumer<String> onMessage) {
        Map<String, Object> config = instance.config();
        var consumerFactory =
                new DefaultKafkaConsumerFactory<String, String>(buildConsumerProperties(config));
        var containerProperties = new ContainerProperties(String.valueOf(config.get("topic")));
        containerProperties.setMessageListener(
                (MessageListener<String, String>) record -> onMessage.accept(record.value()));
        var container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        container.start();
        return container::stop;
    }

    /**
     * @param config a validated {@code kafka} connector instance config (D-KAFKA-AUTH,
     *     requirements: {@code PLAINTEXT} or {@code SASL_SSL}). Package-private so
     *     {@code T-KR-PLAINTEXT-PROPS}/{@code T-KR-SASL-PROPS} can assert on it directly without
     *     starting a real consumer.
     */
    static Map<String, Object> buildConsumerProperties(Map<String, Object> config) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.get("bootstrapServers"));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, config.get("groupId"));
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        String securityProtocol = String.valueOf(config.get("securityProtocol"));
        props.put("security.protocol", securityProtocol);
        if ("SASL_SSL".equals(securityProtocol)) {
            props.put("sasl.mechanism", "PLAIN");
            props.put("sasl.jaas.config", jaasConfig(config));
        }
        return props;
    }

    private static String jaasConfig(Map<String, Object> config) {
        return "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";"
                .formatted(escapeJaasValue(config.get("saslUsername")), escapeJaasValue(config.get("saslPassword")));
    }

    /**
     * Escapes {@code \} and {@code "} per the JAAS config quoted-string grammar (found in
     * adversarial review, connecteurs-cible lot B1) — without this, a username/password
     * containing a double quote silently breaks the generated {@code sasl.jaas.config} string
     * instead of being carried as literal credential text.
     */
    private static String escapeJaasValue(Object value) {
        return String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
