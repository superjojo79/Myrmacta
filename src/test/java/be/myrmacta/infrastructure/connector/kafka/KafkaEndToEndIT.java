package be.myrmacta.infrastructure.connector.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.usecase.ConnectorTriggerRuntimeImpl;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.port.in.CreateDossierUseCase;
import be.myrmacta.domain.dossier.port.in.GetDossierUseCase;
import be.myrmacta.domain.workflow.model.OutboxEvent;
import be.myrmacta.domain.workflow.model.TriggerOutboxPayload;
import be.myrmacta.domain.workflow.port.out.OutboxRepository;
import be.myrmacta.infrastructure.connector.builtin.KafkaConnector;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import be.myrmacta.infrastructure.connector.registry.ConnectorRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * IT-KAFKA-END-TO-END (testing doc) : chaîne réelle {@link KafkaConsumerReconciler} →
 * {@link KafkaConnector} → {@link ConnectorTriggerRuntimeImpl} → dossier réellement créé et
 * lisible. Seul {@link KafkaConsumerFactory} est un faux (D-KAFKA-TEST = mocké uniquement) — pas
 * de broker réel, la livraison de message est simulée manuellement.
 */
class KafkaEndToEndIT {

    private static final String TENANT = "tenant_dev";

    @Test
    void kafkaInstance_receivesMessage_createsRealDossier_readableAfterwards() {
        var kafkaConnector = new KafkaConnector(new SimpleJsonPathMapper());
        var registry = new ConnectorRegistry(List.of(kafkaConnector));
        registry.init();

        var dossierStore = new InMemoryDossierStore();
        var runtime = new ConnectorTriggerRuntimeImpl(registry, dossierStore, dossierStore, new NoOpOutbox());

        var factory = new FakeKafkaConsumerFactory();
        var reconciler = new KafkaConsumerReconciler(
                new SingleInstanceRepository(instance()),
                runtime,
                new be.myrmacta.infrastructure.config.TenantProperties(TENANT),
                factory,
                JsonMapper.builder().build());

        reconciler.reconcile();
        factory.deliver(instance().id(), "{\"claim\":{\"id\":\"C-1\"}}");

        assertThat(dossierStore.created).hasSize(1);
        assertThat(dossierStore.created.getFirst().fields()).containsEntry("claimNumber", "C-1");
        Dossier reread = dossierStore.get(dossierStore.created.getFirst().id(), AccessContext.system("test"));
        assertThat(reread.fields()).containsEntry("claimNumber", "C-1");
    }

    private static ConnectorInstanceData instance() {
        return new ConnectorInstanceData(
                FIXED_ID,
                TENANT,
                "Kafka",
                "kafka",
                true,
                Map.of(
                        "bootstrapServers", "localhost:9092",
                        "topic", "claims.events",
                        "groupId", "flowforge",
                        "securityProtocol", "PLAINTEXT",
                        "processKey", "importProcess",
                        "dossierTypeCode", "IMPORT",
                        "mapping", Map.of("fields", Map.of("claimNumber", "$.claim.id"))));
    }

    private static final UUID FIXED_ID = UUID.randomUUID();

    private static final class FakeKafkaConsumerFactory implements KafkaConsumerFactory {
        private final Map<UUID, Consumer<String>> callbacks = new java.util.LinkedHashMap<>();

        void deliver(UUID instanceId, String rawValue) {
            callbacks.get(instanceId).accept(rawValue);
        }

        @Override
        public KafkaConsumerHandle start(ConnectorInstanceData instance, Consumer<String> onMessage) {
            callbacks.put(instance.id(), onMessage);
            return () -> {};
        }
    }

    private static final class SingleInstanceRepository
            implements be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository {
        private final ConnectorInstanceData instance;

        SingleInstanceRepository(ConnectorInstanceData instance) {
            this.instance = instance;
        }

        @Override
        public ConnectorInstanceData save(ConnectorInstanceData data) {
            return data;
        }

        @Override
        public Optional<ConnectorInstanceData> findByIdAndTenantId(UUID instanceId, String tenantId) {
            return Optional.of(instance);
        }

        @Override
        public Optional<ConnectorInstanceData> findByIdAndTenantIdIncludingDeleted(UUID instanceId, String tenantId) {
            return Optional.of(instance);
        }

        @Override
        public List<ConnectorInstanceData> listByTenantId(String tenantId) {
            return List.of(instance);
        }

        @Override
        public void softDelete(UUID instanceId, String tenantId) {}
    }

    private static final class NoOpOutbox implements OutboxRepository {
        @Override
        public OutboxEvent savePending(TriggerOutboxPayload payload) {
            return OutboxEvent.pending(payload, Instant.parse("2026-09-14T00:00:00Z"));
        }

        @Override
        public List<OutboxEvent> lockNextPending(int batchSize) {
            return List.of();
        }

        @Override
        public void markProcessed(UUID eventId) {}

        @Override
        public void markDead(UUID eventId, String error) {}

        @Override
        public void recordFailure(UUID eventId, String error, int maxAttempts) {}

        @Override
        public int purgeProcessedOlderThanDays(int days) {
            return 0;
        }

        @Override
        public Optional<Instant> oldestPendingCreatedAt() {
            return Optional.empty();
        }
    }

    private static final class InMemoryDossierStore implements CreateDossierUseCase, GetDossierUseCase {
        final List<Dossier> created = new ArrayList<>();

        @Override
        public Dossier create(CreateDossierCommand command, AccessContext ctx) {
            Dossier dossier = new Dossier(
                    UUID.randomUUID(),
                    command.tenantId(),
                    "REF-" + created.size(),
                    command.dossierTypeCode(),
                    command.label(),
                    DossierStatus.OUVERT,
                    command.fields(),
                    null,
                    0L,
                    command.createdBy(),
                    Instant.parse("2026-09-14T00:00:00Z"),
                    null,
                    null,
                    null,
                    null,
                    null);
            created.add(dossier);
            return dossier;
        }

        @Override
        public Dossier get(UUID id, AccessContext ctx) {
            return created.stream()
                    .filter(d -> d.id().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new DossierNotFoundException(id.toString()));
        }

        @Override
        public Dossier getByReference(String tenantId, String reference, AccessContext ctx) {
            return created.stream()
                    .filter(d -> d.reference().equals(reference))
                    .findFirst()
                    .orElseThrow(() -> new DossierNotFoundException(reference));
        }
    }
}
