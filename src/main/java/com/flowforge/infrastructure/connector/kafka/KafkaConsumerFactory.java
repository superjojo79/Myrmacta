package com.flowforge.infrastructure.connector.kafka;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import java.util.function.Consumer;

/**
 * Starts a real Kafka consumer bound to one connector instance's config — kept as a narrow seam
 * (like {@code SftpClientPort} for {@code batch-import}) so {@code KafkaConsumerReconciler}'s
 * diff/lifecycle logic is testable with a fake, without a real broker (D-KAFKA-TEST = mocké
 * uniquement, requirements).
 */
public interface KafkaConsumerFactory {

    /**
     * @param onMessage called with the raw (String) value of each consumed record; the factory
     *     itself does no JSON parsing — that is {@code KafkaConsumerReconciler}'s job.
     */
    KafkaConsumerHandle start(ConnectorInstanceData instance, Consumer<String> onMessage);
}
