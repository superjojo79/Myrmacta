package com.flowforge.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.dossier.model.AbortReservedReason;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.model.WorkflowAlreadyRunningException;
import com.flowforge.domain.dossier.port.in.AttachWorkflowUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.workflow.model.StatusOutcome;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * La règle « un seul workflow actif par dossier » sous parallélisme réel — fiabilisation-tests-plateforme,
 * D-PARALLELISM-TEST : PostgreSQL embarqué + threads réels, exercice direct de {@link AttachWorkflowUseCase#reserve}
 * concurremment sur le même dossier. La garantie repose sur l'index unique partiel
 * {@code uq_dossier_workflow_link_active} (index partiel PostgreSQL natif, {@code 002-workflow-tables.yaml}) ; ce test
 * prouve à la fois qu'un seul thread gagne la course et que les perdants reçoivent
 * {@link WorkflowAlreadyRunningException}, pas une exception de persistance brute.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class ConcurrentWorkflowReservationTest {

    private static final int CONCURRENT_RESERVERS = 8;

    @Autowired
    private AttachWorkflowUseCase attachWorkflow;

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private WorkflowTypeConfigRepository configRepository;

    @Autowired
    private DossierWorkflowLinkRepository linkRepository;

    @Test
    void concurrentReserve_onlyOneWinsAndLosersGetWorkflowAlreadyRunning() throws Exception {
        String processKey = "concurrent-reserve-" + UUID.randomUUID();
        configRepository.save(smokeConfig(processKey));
        Dossier dossier = dossier(DossierStatus.OUVERT);
        dossierRepository.save(dossier);

        List<ReserveOutcome> outcomes = reserveConcurrently(dossier.id(), processKey);

        long successes = outcomes.stream().filter(ReserveOutcome::success).count();
        long alreadyRunningFailures = outcomes.stream()
                .filter(outcome -> !outcome.success())
                .filter(outcome -> outcome.failure() instanceof WorkflowAlreadyRunningException)
                .count();

        assertThat(successes).as("un seul thread doit gagner la réservation").isEqualTo(1);
        assertThat(alreadyRunningFailures)
                .as("tous les perdants doivent recevoir WorkflowAlreadyRunningException, pas une exception brute")
                .isEqualTo(CONCURRENT_RESERVERS - 1);
        assertThat(linkRepository.findByDossierIdOrderBySequence(dossier.id())).hasSize(1);
    }

    @Test
    void afterAbort_activeReservationBecomesFreeForANewConcurrentBatch() throws Exception {
        String processKey = "concurrent-reserve-reopen-" + UUID.randomUUID();
        configRepository.save(smokeConfig(processKey));
        Dossier dossier = dossier(DossierStatus.OUVERT);
        dossierRepository.save(dossier);

        DossierWorkflowLink firstWinner = attachWorkflow.reserve(dossier.id(), processKey, "manual", "starter");
        attachWorkflow.abortReserved(firstWinner.id(), AbortReservedReason.ORPHAN_START_FAILED);

        List<ReserveOutcome> outcomes = reserveConcurrently(dossier.id(), processKey);

        long successes = outcomes.stream().filter(ReserveOutcome::success).count();
        assertThat(successes).as("après clôture, une nouvelle réservation concurrente redevient possible").isEqualTo(1);
        assertThat(linkRepository.findByDossierIdOrderBySequence(dossier.id())).hasSize(2);
    }

    private List<ReserveOutcome> reserveConcurrently(UUID dossierId, String processKey) throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_RESERVERS);
        try {
            List<Future<ReserveOutcome>> futures = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_RESERVERS; i++) {
                String triggerRef = "trigger-" + i;
                futures.add(pool.submit(() -> {
                    startLatch.await();
                    try {
                        DossierWorkflowLink link = attachWorkflow.reserve(dossierId, processKey, triggerRef, "starter");
                        return ReserveOutcome.success(link);
                    } catch (RuntimeException ex) {
                        return ReserveOutcome.failure(ex);
                    }
                }));
            }
            startLatch.countDown();

            List<ReserveOutcome> outcomes = new ArrayList<>();
            for (Future<ReserveOutcome> future : futures) {
                outcomes.add(future.get(10, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private record ReserveOutcome(DossierWorkflowLink link, RuntimeException failure) {
        static ReserveOutcome success(DossierWorkflowLink link) {
            return new ReserveOutcome(link, null);
        }

        static ReserveOutcome failure(RuntimeException ex) {
            return new ReserveOutcome(null, ex);
        }

        boolean success() {
            return link != null;
        }
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
                "DOS-CWR-" + id.toString().replace("-", "").substring(0, 12),
                "WF",
                "Concurrent workflow reservation dossier",
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
