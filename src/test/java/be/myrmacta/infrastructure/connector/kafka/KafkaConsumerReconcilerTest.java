package be.myrmacta.infrastructure.connector.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.in.ConnectorTriggerRuntime;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class KafkaConsumerReconcilerTest {

    private static final String TENANT = "tenant_dev";

    private ConnectorInstanceRepository connectorInstanceRepository;
    private ConnectorTriggerRuntime connectorTriggerRuntime;
    private FakeKafkaConsumerFactory consumerFactory;
    private KafkaConsumerReconciler reconciler;

    @BeforeEach
    void setUp() {
        connectorInstanceRepository = mock(ConnectorInstanceRepository.class);
        connectorTriggerRuntime = mock(ConnectorTriggerRuntime.class);
        consumerFactory = new FakeKafkaConsumerFactory();
        reconciler = new KafkaConsumerReconciler(
                connectorInstanceRepository,
                connectorTriggerRuntime,
                new TenantProperties(TENANT),
                consumerFactory,
                JsonMapper.builder().build());
    }

    @Test
    void reconcile_newEnabledInstance_startsConsumer() {
        var instance = kafkaInstance();
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(instance));

        reconciler.reconcile();

        assertThat(consumerFactory.started).containsExactly(instance);
    }

    @Test
    void reconcile_disabledInstance_isNeverStarted() {
        var instance = kafkaInstance(false);
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(instance));

        reconciler.reconcile();

        assertThat(consumerFactory.started).isEmpty();
    }

    @Test
    void reconcile_otherConnectorTypes_areIgnored() {
        var instance = new ConnectorInstanceData(
                UUID.randomUUID(), TENANT, "http", "http-rest", true, Map.of());
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(instance));

        reconciler.reconcile();

        assertThat(consumerFactory.started).isEmpty();
    }

    @Test
    void reconcile_samePollTwice_noChange_doesNotRestart() {
        var instance = kafkaInstance();
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(instance));

        reconciler.reconcile();
        reconciler.reconcile();

        assertThat(consumerFactory.started).hasSize(1);
        assertThat(consumerFactory.stoppedCount(instance.id())).isZero();
    }

    @Test
    void reconcile_instanceDisabledAfterwards_stopsConsumer() {
        var enabled = kafkaInstance(true);
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(enabled));
        reconciler.reconcile();

        var disabled = new ConnectorInstanceData(
                enabled.id(), TENANT, enabled.name(), "kafka", false, enabled.config());
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(disabled));
        reconciler.reconcile();

        assertThat(consumerFactory.stoppedCount(enabled.id())).isEqualTo(1);
    }

    @Test
    void reconcile_instanceDeleted_stopsConsumer() {
        var instance = kafkaInstance();
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(instance));
        reconciler.reconcile();

        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of());
        reconciler.reconcile();

        assertThat(consumerFactory.stoppedCount(instance.id())).isEqualTo(1);
    }

    @Test
    void reconcile_configChanged_stopsOldConsumer_startsNewOne() {
        var v1 = kafkaInstance();
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(v1));
        reconciler.reconcile();

        Map<String, Object> changedConfig = new LinkedHashMap<>(v1.config());
        changedConfig.put("topic", "other-topic");
        var v2 = new ConnectorInstanceData(v1.id(), TENANT, v1.name(), "kafka", true, changedConfig);
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(v2));
        reconciler.reconcile();

        assertThat(consumerFactory.stoppedCount(v1.id())).isEqualTo(1);
        assertThat(consumerFactory.started).containsExactly(v1, v2);
    }

    @Test
    void reconcile_oneInstanceFailsToStart_othersStillStart() {
        var failing = kafkaInstance("failing");
        var healthy = kafkaInstance("healthy");
        consumerFactory.failFor(failing.id());
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(failing, healthy));

        reconciler.reconcile();

        assertThat(consumerFactory.started).containsExactly(healthy);
    }

    @Test
    void message_validJson_dispatchesToTriggerRuntime() {
        var instance = kafkaInstance();
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(instance));
        reconciler.reconcile();

        consumerFactory.deliver(instance.id(), "{\"claim\":{\"id\":\"C-1\"}}");

        verify(connectorTriggerRuntime).onIncomingEvent(any(), any());
    }

    @Test
    void message_malformedJson_isSkipped_doesNotThrow_subsequentMessageStillProcessed() {
        var instance = kafkaInstance();
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(instance));
        reconciler.reconcile();

        consumerFactory.deliver(instance.id(), "not-json-{{{");
        consumerFactory.deliver(instance.id(), "{\"claim\":{\"id\":\"C-2\"}}");

        verify(connectorTriggerRuntime).onIncomingEvent(any(), any());
    }

    @Test
    void message_triggerRuntimeThrows_isSkipped_doesNotPropagate() {
        var instance = kafkaInstance();
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(instance));
        reconciler.reconcile();
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(connectorTriggerRuntime)
                .onIncomingEvent(any(), any());

        consumerFactory.deliver(instance.id(), "{\"claim\":{\"id\":\"C-1\"}}");

        // onIncomingEvent was invoked and threw internally — the assertion is that deliver()
        // itself did not propagate that exception (reaching this line proves it).
        verify(connectorTriggerRuntime).onIncomingEvent(any(), any());
    }

    private static ConnectorInstanceData kafkaInstance() {
        return kafkaInstance(true);
    }

    private static ConnectorInstanceData kafkaInstance(boolean enabled) {
        return kafkaInstance(enabled ? "kafka" : "kafka-disabled", enabled);
    }

    private static ConnectorInstanceData kafkaInstance(String name) {
        return kafkaInstance(name, true);
    }

    private static ConnectorInstanceData kafkaInstance(String name, boolean enabled) {
        return new ConnectorInstanceData(
                UUID.randomUUID(),
                TENANT,
                name,
                "kafka",
                enabled,
                Map.of(
                        "bootstrapServers", "localhost:9092",
                        "topic", "claims.events",
                        "groupId", "flowforge",
                        "securityProtocol", "PLAINTEXT",
                        "processKey", "p",
                        "dossierTypeCode", "T"));
    }

    /** No real broker (D-KAFKA-TEST) — captures start/stop calls and lets the test manually
     * simulate message delivery. */
    private static final class FakeKafkaConsumerFactory implements KafkaConsumerFactory {
        final List<ConnectorInstanceData> started = new ArrayList<>();
        private final Map<UUID, Integer> stopCounts = new LinkedHashMap<>();
        private final Map<UUID, Consumer<String>> callbacks = new LinkedHashMap<>();
        private final java.util.Set<UUID> shouldFail = new java.util.HashSet<>();

        void failFor(UUID instanceId) {
            shouldFail.add(instanceId);
        }

        int stoppedCount(UUID instanceId) {
            return stopCounts.getOrDefault(instanceId, 0);
        }

        void deliver(UUID instanceId, String rawValue) {
            callbacks.get(instanceId).accept(rawValue);
        }

        @Override
        public KafkaConsumerHandle start(ConnectorInstanceData instance, Consumer<String> onMessage) {
            if (shouldFail.contains(instance.id())) {
                throw new IllegalStateException("simulated start failure for " + instance.id());
            }
            started.add(instance);
            callbacks.put(instance.id(), onMessage);
            return () -> stopCounts.merge(instance.id(), 1, Integer::sum);
        }
    }
}
