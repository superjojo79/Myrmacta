package com.flowforge.infrastructure.connector.kafka;

/** Handle on a running per-instance Kafka consumer — lets {@code KafkaConsumerReconciler} stop it
 * without depending on any concrete Spring Kafka type (testability, D-KAFKA-TEST). */
@FunctionalInterface
public interface KafkaConsumerHandle {

    void stop();
}
