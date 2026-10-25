package be.myrmacta.infrastructure.connector;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.connector.port.out.ConnectorScheduleStateRepository;
import be.myrmacta.domain.connector.port.out.SftpClientPort;
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
import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.connector.builtin.BatchImportConnector;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import be.myrmacta.infrastructure.connector.registry.ConnectorRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/**
 * IT-BI-END-TO-END (testing doc) : chaîne réelle {@code ManageConnectorInstanceUseCase.create}
 * (simulé par construction directe de l'instance, le use case admin n'étant pas le sujet ici) →
 * {@link BatchImportConnectorTriggerJob} → {@link ConnectorTriggerRuntimeImpl} (réel, pas mocké,
 * contrairement à {@code BatchImportConnectorTriggerJobTest}) → dossier réellement créé et
 * lisible. Seul {@code SftpClientPort} est un faux (D-SFTP-TEST = mocké uniquement) ; le reste de
 * la chaîne (connecteur, runtime, catalogue) est le code réel.
 */
class BatchImportEndToEndIT {

    private static final String TENANT = "tenant_dev";

    @Test
    void batchImportInstance_firesJob_createsRealDossiers_readableAfterwards() {
        var sftp = new FakeSftpClientPort();
        sftp.files.put(
                "/in",
                List.of(new SftpClientPort.RemoteFile("/in/clients.csv", 64)));
        sftp.content.put("/in/clients.csv", "name\nAlice\nBob\n".getBytes(StandardCharsets.UTF_8));

        var batchImportConnector =
                new BatchImportConnector(sftp, new SimpleJsonPathMapper(), new ConnectorProperties(
                        null, null, null, null, null, null));
        var registry = new ConnectorRegistry(List.of(batchImportConnector));
        registry.init();

        var dossierStore = new InMemoryDossierStore();
        var runtime = new ConnectorTriggerRuntimeImpl(registry, dossierStore, dossierStore, new NoOpOutbox());

        var instanceRepository = new SingleInstanceRepository(instance());
        var scheduleState = new InMemoryScheduleState();
        var job = new BatchImportConnectorTriggerJob(instanceRepository, scheduleState, runtime, new TenantProperties(TENANT));

        job.process(Instant.parse("2026-09-14T00:00:00Z"));

        assertThat(dossierStore.created).hasSize(2);
        assertThat(dossierStore.created.get(0).fields()).containsEntry("clientName", "Alice");
        assertThat(dossierStore.created.get(1).fields()).containsEntry("clientName", "Bob");
        Dossier reread = dossierStore.get(dossierStore.created.get(0).id(), AccessContext.system("test"));
        assertThat(reread.fields()).containsEntry("clientName", "Alice");
    }

    private static ConnectorInstanceData instance() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("sftpHost", "sftp.example.com");
        config.put("sftpPort", 22);
        config.put("sftpUsername", "myrmacta");
        config.put("sftpAuthType", "PASSWORD");
        config.put("sftpPassword", "secret12345678901234");
        config.put("sftpHostKeyFingerprint", "SHA256:abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG");
        config.put("remoteDirectory", "/in");
        config.put("filenamePattern", "*.csv");
        config.put("archiveDirectory", "/archive");
        config.put("cronExpression", "0 * * * * *");
        config.put("processKey", "importProcess");
        config.put("dossierTypeCode", "IMPORT");
        config.put("mapping", Map.of("fields", Map.of("clientName", "$.name")));
        return new ConnectorInstanceData(UUID.randomUUID(), TENANT, "Batch", "batch-import", true, config);
    }

    private static final class FakeSftpClientPort implements SftpClientPort {
        final Map<String, List<RemoteFile>> files = new LinkedHashMap<>();
        final Map<String, byte[]> content = new LinkedHashMap<>();

        @Override
        public List<RemoteFile> listFiles(
                be.myrmacta.domain.connector.model.SftpConnectionConfig config,
                String remoteDirectory,
                String filenamePattern) {
            return files.getOrDefault(remoteDirectory, List.of());
        }

        @Override
        public byte[] download(
                be.myrmacta.domain.connector.model.SftpConnectionConfig config, String remotePath, long maxBytes) {
            return content.getOrDefault(remotePath, new byte[0]);
        }

        @Override
        public void archive(
                be.myrmacta.domain.connector.model.SftpConnectionConfig config,
                String remotePath,
                String archiveDirectory) {
            // no-op — archiving itself is covered by BatchImportConnectorTest
        }
    }

    private static final class SingleInstanceRepository implements ConnectorInstanceRepository {
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

    private static final class InMemoryScheduleState implements ConnectorScheduleStateRepository {
        private final Map<UUID, Instant> lastTriggeredAt = new ConcurrentHashMap<>();

        @Override
        public Optional<Instant> findLastTriggeredAt(UUID connectorInstanceId) {
            return Optional.ofNullable(lastTriggeredAt.get(connectorInstanceId));
        }

        @Override
        public void upsert(UUID connectorInstanceId, Instant triggeredAt) {
            lastTriggeredAt.put(connectorInstanceId, triggeredAt);
        }

        @Override
        public boolean claimTrigger(UUID connectorInstanceId, Instant expectedLast, Instant claimedAt) {
            if (expectedLast == null) {
                return lastTriggeredAt.putIfAbsent(connectorInstanceId, claimedAt) == null;
            }
            return lastTriggeredAt.replace(connectorInstanceId, expectedLast, claimedAt);
        }

        @Override
        public void releaseTrigger(UUID connectorInstanceId, Instant previousLast, Instant claimedAt) {
            if (previousLast == null) {
                lastTriggeredAt.remove(connectorInstanceId, claimedAt);
            } else {
                lastTriggeredAt.replace(connectorInstanceId, claimedAt, previousLast);
            }
        }
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

    /** In-memory {@link CreateDossierUseCase}/{@link GetDossierUseCase} — proves real end-to-end
     * dossier creation/read-back without depending on {@code CreateDossierUseCaseImpl}'s own ACL/
     * validation machinery, which is exercised elsewhere. */
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
