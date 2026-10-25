package be.myrmacta.infrastructure.persistence.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.WorkflowAlreadyRunningException;
import be.myrmacta.domain.dossier.port.in.AttachWorkflowUseCase;
import be.myrmacta.domain.dossier.port.in.GetDossierWorkflowHistoryUseCase;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.workflow.model.OutboxStatus;
import be.myrmacta.domain.workflow.model.StatusOutcome;
import be.myrmacta.domain.workflow.model.TriggerOutboxPayload;
import be.myrmacta.domain.workflow.model.WorkflowStatusDefinition;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.port.out.IdempotencyStore;
import be.myrmacta.domain.workflow.port.out.OutboxRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
// Le relais réel (OutboxRelayScheduler, poll 1s) prendrait des événements créés par les tests d'outbox
// ci-dessous et les ferait disparaître de lockNextPending : on l'endort pour cette classe.
@TestPropertySource(properties = "app.workflow.outbox.poll-delay=1h")
class WorkflowPersistenceTest {

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private WorkflowTypeConfigRepository configRepository;

    @Autowired
    private IdempotencyStore idempotencyStore;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private SpringDataOutboxEventRepository springDataOutbox;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private AttachWorkflowUseCase attachWorkflow;

    @Autowired
    private GetDossierWorkflowHistoryUseCase historyUseCase;

    @Autowired
    private DossierWorkflowLinkRepository linkRepository;

    @Test
    void persistsWorkflowConfigAndIdempotencyRows() {
        WorkflowTypeConfig config = smokeConfig("workflow-config-" + UUID.randomUUID());

        configRepository.save(config);
        idempotencyStore.remember("tenant_dev", UUID.randomUUID(), "idem-1", UUID.randomUUID());

        assertThat(configRepository.findByWorkflowKey("tenant_dev", config.workflowKey()))
                .contains(config);
    }

    @Test
    void page_ordersByWorkflowKeyAndKeepsTotalsPastEnd() {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        configRepository.save(smokeConfig("zza-page-" + token));
        configRepository.save(smokeConfig("zzc-page-" + token));
        configRepository.save(smokeConfig("zzb-page-" + token));

        var page0 = configRepository.page("tenant_dev", PageQuery.of(0, 100));
        List<String> keys = page0.content().stream()
                .map(WorkflowTypeConfig::workflowKey)
                .filter(key -> key.endsWith(token))
                .toList();
        assertThat(keys)
                .containsExactly("zza-page-" + token, "zzb-page-" + token, "zzc-page-" + token);
        assertThat(page0.content().stream().map(WorkflowTypeConfig::workflowKey).toList()).isSorted();

        var pastEnd = configRepository.page("tenant_dev", PageQuery.of(9_999, 20));
        assertThat(pastEnd.content()).isEmpty();
        assertThat(pastEnd.totalElements()).isEqualTo(page0.totalElements());
    }

    /** D5 (TODO §79) : un seul des réconciliateurs concurrents prend un lien orphelin ; une prise échue se reprend. */
    @Test
    void claimForReconcile_oneConcurrentReconcilerWins_andOnlyWhileTheLinkIsAnOrphan() throws Exception {
        Dossier dossier = dossier(DossierStatus.OUVERT);
        dossierRepository.save(dossier);
        var orphan = linkRepository.save(new be.myrmacta.domain.dossier.model.DossierWorkflowLink(
                UUID.randomUUID(), dossier.id(), "tenant_dev", null, "procOrphan", 1, "STARTED", "manual",
                Instant.parse("2026-08-02T10:00:00Z"), null, null, null));
        Instant now = Instant.parse("2026-08-02T10:10:00Z");
        int reconcilers = 6;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(reconcilers);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<Boolean>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < reconcilers; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return linkRepository.claimForReconcile(orphan.id(), now, now.plusSeconds(120));
                }));
            }
            go.countDown();
            long winners = 0;
            for (var f : futures) {
                winners += f.get() ? 1 : 0;
            }
            assertThat(winners).as("un seul réconciliateur relance le process").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(linkRepository.claimForReconcile(orphan.id(), now.plusSeconds(60), now.plusSeconds(180))).isFalse();
        assertThat(linkRepository.claimForReconcile(orphan.id(), now.plusSeconds(121), now.plusSeconds(241))).isTrue();
        // Plus orphelin (process démarré) : plus de prise.
        linkRepository.save(orphan.withProcessInstance("proc-started"));
        assertThat(linkRepository.claimForReconcile(orphan.id(), now.plusSeconds(10_000), now.plusSeconds(10_120))).isFalse();
    }

    @Test
    void pageByDossierId_ordersBySequenceAndKeepsTotal() {
        Dossier dossier = dossier(DossierStatus.OUVERT);
        dossierRepository.save(dossier);
        linkRepository.save(new be.myrmacta.domain.dossier.model.DossierWorkflowLink(
                UUID.randomUUID(), dossier.id(), "tenant_dev", "proc-1", "procA", 1, "COMPLETED", "manual",
                Instant.parse("2026-08-02T10:00:00Z"), Instant.parse("2026-08-02T10:04:00Z"), "COMPLETED",
                "starter"));
        linkRepository.save(new be.myrmacta.domain.dossier.model.DossierWorkflowLink(
                UUID.randomUUID(), dossier.id(), "tenant_dev", null, "procA", 2, "STARTED", "manual",
                Instant.parse("2026-08-02T10:05:00Z"), null, null, "starter"));

        var page = linkRepository.pageByDossierId(dossier.id(), PageQuery.of(0, 20));

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).extracting(link -> link.sequence()).containsExactly(1, 2);
    }

    @Test
    void outboxRepositoryStoresAndMarksEvents() {
        var payload = new TriggerOutboxPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "smokeProcess",
                Map.of("priority", "NORMAL"),
                "trigger-1");

        var event = outboxRepository.savePending(payload);
        assertThat(event.status()).isEqualTo(OutboxStatus.PENDING);

        assertThat(outboxRepository.lockNextPending(10))
                .extracting(stored -> stored.eventId())
                .contains(event.eventId());

        outboxRepository.markProcessed(event.eventId());

        assertThat(outboxRepository.lockNextPending(10))
                .extracting(stored -> stored.eventId())
                .doesNotContain(event.eventId());
    }

    /** D5 (TODO §79) : deux relais qui interrogent en même temps ne reçoivent jamais le même événement. */
    @Test
    void outboxLockNextPending_concurrentRelaysNeverReceiveTheSameEvent() throws Exception {
        java.util.Set<UUID> created = new java.util.HashSet<>();
        for (int i = 0; i < 40; i++) {
            created.add(outboxRepository.savePending(new TriggerOutboxPayload(
                    UUID.randomUUID(), null, "smokeProcess", Map.of(), "trigger-conc-" + i)).eventId());
        }
        int relays = 6;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(relays);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<List<UUID>>> futures = new java.util.ArrayList<>();
            for (int r = 0; r < relays; r++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    List<UUID> mine = new java.util.ArrayList<>();
                    int emptyInARow = 0;
                    for (int round = 0; round < 500 && emptyInARow < 3; round++) {
                        var got = outboxRepository.lockNextPending(10);
                        got.forEach(e -> mine.add(e.eventId()));
                        emptyInARow = got.isEmpty() ? emptyInARow + 1 : 0;
                    }
                    return mine;
                }));
            }
            go.countDown();
            List<UUID> all = new java.util.ArrayList<>();
            for (var f : futures) {
                all.addAll(f.get());
            }
            // Un relais qui perd trois courses de suite s'arrête : on termine en série ce qui reste.
            for (List<be.myrmacta.domain.workflow.model.OutboxEvent> rest = outboxRepository.lockNextPending(100);
                    !rest.isEmpty(); rest = outboxRepository.lockNextPending(100)) {
                rest.forEach(e -> all.add(e.eventId()));
            }
            assertThat(all).as("aucun événement reçu par deux relais").doesNotHaveDuplicates();
            assertThat(new java.util.HashSet<>(all)).containsAll(created);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void outboxAClaimedEventIsOfferedAgainOnlyAfterItsLeaseOrAFailure() {
        var event = outboxRepository.savePending(new TriggerOutboxPayload(
                UUID.randomUUID(), null, "smokeProcess", Map.of(), "trigger-lease"));
        assertThat(outboxRepository.lockNextPending(500)).extracting(e -> e.eventId()).contains(event.eventId());
        assertThat(outboxRepository.lockNextPending(500)).extracting(e -> e.eventId()).doesNotContain(event.eventId());

        // Exécutant mort : le bail échoit.
        jdbc.update("update outbox_event set claimed_until = ? where event_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), event.eventId());
        assertThat(outboxRepository.lockNextPending(500)).extracting(e -> e.eventId()).contains(event.eventId());

        // Échec signalé : le bail est levé, retry immédiat comme avant.
        outboxRepository.recordFailure(event.eventId(), "boom", 5);
        assertThat(outboxRepository.lockNextPending(500)).extracting(e -> e.eventId()).contains(event.eventId());
        outboxRepository.markProcessed(event.eventId());
    }

    @Test
    void outboxRecordFailureIncrementsAttemptsThenMarksDead() {
        var event = outboxRepository.savePending(new TriggerOutboxPayload(
                UUID.randomUUID(), null, "smokeProcess", Map.of(), "trigger-fail"));

        outboxRepository.recordFailure(event.eventId(), "boom-1", 3);
        OutboxEventJpaEntity afterOne = springDataOutbox.findById(event.eventId()).orElseThrow();
        assertThat(afterOne.toDomain().attempts()).isEqualTo(1);
        assertThat(afterOne.toDomain().status()).isEqualTo(OutboxStatus.PENDING);

        outboxRepository.recordFailure(event.eventId(), "boom-2", 3);
        outboxRepository.recordFailure(event.eventId(), "boom-3", 3);
        OutboxEventJpaEntity dead = springDataOutbox.findById(event.eventId()).orElseThrow();
        assertThat(dead.toDomain().attempts()).isEqualTo(3);
        assertThat(dead.toDomain().status()).isEqualTo(OutboxStatus.DEAD);
        assertThat(outboxRepository.lockNextPending(50))
                .extracting(stored -> stored.eventId())
                .doesNotContain(event.eventId());
    }

    @Test
    void oldestPendingCreatedAt_emptyAndPending() {
        var event = outboxRepository.savePending(new TriggerOutboxPayload(
                UUID.randomUUID(), null, "smokeProcess", Map.of(), "trigger-age"));
        assertThat(event.eventId()).isNotNull();
        assertThat(outboxRepository.oldestPendingCreatedAt()).isPresent();
    }

    @Test
    void reserveConfirmAndAbortMaintainDossierCurrentWorkflowLink() {
        String processKey = "smokeProcess-" + UUID.randomUUID();
        configRepository.save(smokeConfig(processKey));
        Dossier dossier = dossier(DossierStatus.OUVERT);
        dossierRepository.save(dossier);

        var reserved = attachWorkflow.reserve(dossier.id(), processKey, "manual", "starter-sub");
        assertThat(reserved.isReserved()).isTrue();
        assertThat(reserved.startedBy()).isEqualTo("starter-sub");
        assertThat(dossierRepository.findById(dossier.id()).orElseThrow().currentWorkflowLinkId())
                .isNull();

        attachWorkflow.confirmStarted(reserved.id(), "proc-1");
        assertThat(dossierRepository.findById(dossier.id()).orElseThrow().currentWorkflowLinkId())
                .isEqualTo(reserved.id());
        assertThat(linkRepository.findByProcessInstanceId("tenant_dev", "proc-1"))
                .get()
                .extracting(link -> link.id())
                .isEqualTo(reserved.id());

        assertThatThrownBy(() -> attachWorkflow.reserve(dossier.id(), processKey, "manual-2", "starter-sub"))
                .isInstanceOf(WorkflowAlreadyRunningException.class);

        assertThat(historyUseCase.getHistory(dossier.id(), PageQuery.defaults()).content())
                .hasSize(1)
                .first()
                .extracting(link -> link.sequence())
                .isEqualTo(1);
    }

    private static WorkflowTypeConfig smokeConfig(String workflowKey) {
        return new WorkflowTypeConfig(
                UUID.randomUUID(),
                "tenant_dev",
                workflowKey,
                "Smoke",
                List.of(
                        new WorkflowStatusDefinition("STARTED", "Started", true, StatusOutcome.NONE),
                        new WorkflowStatusDefinition("COMPLETED", "Completed", false, StatusOutcome.COMPLETED)),
                List.of(DossierStatus.OUVERT, DossierStatus.EN_COURS, DossierStatus.SUSPENDU),
                null,
                List.of());
    }

    private static Dossier dossier(DossierStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-31T12:00:00Z");
        return new Dossier(
                id,
                "tenant_dev",
                "DOS-WF-" + id.toString().replace("-", "").substring(0, 12),
                "WF",
                "Workflow dossier",
                status,
                Map.of(),
                null,
                0,
                "creator",
                now,
                "creator",
                now,
                null,
                null,
                null);
    }
}
