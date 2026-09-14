package com.flowforge.infrastructure.connector.kafka;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.port.in.ConnectorTriggerRuntime;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.infrastructure.config.TenantProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Decides which {@code kafka}-type connector instances should have a running consumer — same
 * poll-based pattern as {@code SchedulerConnectorTriggerJob}/{@code BatchImportConnectorTriggerJob}
 * (D-KAFKA-LIFECYCLE, design), not event-driven: {@code ManageConnectorInstanceUseCaseImpl} has no
 * lifecycle hook today, and adding one would touch a class shared by every connector type. Each
 * reconcile diffs desired (enabled {@code kafka} instances) against running consumers: starts
 * missing ones, stops removed/disabled ones, restarts ones whose config changed.
 */
@Component
class KafkaConsumerReconciler {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerReconciler.class);
    private static final String KAFKA_TYPE = "kafka";

    private final ConnectorInstanceRepository connectorInstanceRepository;
    private final ConnectorTriggerRuntime connectorTriggerRuntime;
    private final TenantProperties tenantProperties;
    private final KafkaConsumerFactory consumerFactory;
    private final ObjectMapper objectMapper;
    private final Map<UUID, Running> running = new ConcurrentHashMap<>();

    KafkaConsumerReconciler(
            ConnectorInstanceRepository connectorInstanceRepository,
            ConnectorTriggerRuntime connectorTriggerRuntime,
            TenantProperties tenantProperties,
            KafkaConsumerFactory consumerFactory,
            ObjectMapper objectMapper) {
        this.connectorInstanceRepository =
                Objects.requireNonNull(connectorInstanceRepository, "connectorInstanceRepository");
        this.connectorTriggerRuntime = Objects.requireNonNull(connectorTriggerRuntime, "connectorTriggerRuntime");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.consumerFactory = Objects.requireNonNull(consumerFactory, "consumerFactory");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Scheduled(fixedDelayString = "${app.connector.kafka.reconcile-interval:30s}")
    void reconcile() {
        Map<UUID, ConnectorInstanceData> desired = new LinkedHashMap<>();
        for (ConnectorInstanceData instance : connectorInstanceRepository.listByTenantId(tenantProperties.id())) {
            if (KAFKA_TYPE.equals(instance.connectorType()) && instance.enabled()) {
                desired.put(instance.id(), instance);
            }
        }

        for (UUID instanceId : new ArrayList<>(running.keySet())) {
            if (!desired.containsKey(instanceId)) {
                stop(instanceId);
            }
        }

        for (ConnectorInstanceData instance : desired.values()) {
            Running existing = running.get(instance.id());
            if (existing == null) {
                start(instance);
            } else if (!existing.config().equals(instance.config())) {
                stop(instance.id());
                start(instance);
            }
        }
    }

    private void start(ConnectorInstanceData instance) {
        try {
            KafkaConsumerHandle handle = consumerFactory.start(instance, rawValue -> handleMessage(instance, rawValue));
            running.put(instance.id(), new Running(instance.config(), handle));
        } catch (RuntimeException failure) {
            log.warn(
                    "kafka: failed to start consumer for instance {}, will retry next reconcile: {}",
                    instance.id(), failure.getMessage(), failure);
        }
    }

    private void stop(UUID instanceId) {
        Running removed = running.remove(instanceId);
        if (removed == null) {
            return;
        }
        try {
            removed.handle().stop();
        } catch (RuntimeException failure) {
            log.warn("kafka: failed to stop consumer for instance {}: {}", instanceId, failure.getMessage(), failure);
        }
    }

    private void handleMessage(ConnectorInstanceData instance, String rawValue) {
        try {
            Object payload = objectMapper.readValue(rawValue, Object.class);
            connectorTriggerRuntime.onIncomingEvent(instance, payload);
        } catch (RuntimeException failure) {
            log.warn(
                    "kafka: message on topic {} for instance {} failed, skipped: {}",
                    instance.config().get("topic"), instance.id(), failure.getMessage(), failure);
        }
    }

    private record Running(Map<String, Object> config, KafkaConsumerHandle handle) {}
}
