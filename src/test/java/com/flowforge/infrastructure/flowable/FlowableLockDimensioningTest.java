package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.job.service.JobService;
import org.flowable.job.service.impl.asyncexecutor.AsyncExecutor;
import org.flowable.job.service.impl.asyncexecutor.ResetExpiredJobsCmd;
import org.flowable.job.service.impl.cmd.AcquireJobsCmd;
import org.flowable.job.service.impl.persistence.entity.JobEntity;
import org.flowable.job.service.impl.persistence.entity.JobInfoEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Dimensionnement des verrous Flowable (lockOwner/lockExpirationTime) sous contention réelle —
 * fiabilisation-tests-plateforme, D-FLOWABLE-LOCK-TEST : PostgreSQL embarqué + threads réels, exercice direct de
 * {@link AcquireJobsCmd}, la commande que l'AsyncExecutor de Flowable exécute lui-même en
 * production pour acquérir un job. L'exécuteur async intégré est désactivé
 * ({@code flowable.async-executor-activate=false}) pour que seuls les threads du test se
 * disputent le job — scénario contrôlé et déterministe.
 */
@SpringBootTest(properties = "flowable.async-executor-activate=false")
@Import(OidcClientTestConfiguration.class)
class FlowableLockDimensioningTest {

    private static final int CONCURRENT_ACQUIRERS = 8;
    private static final String PROCESS_KEY = "asyncLockDimensioningTest";

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private ManagementService managementService;

    @Autowired
    private ProcessEngineConfigurationImpl processEngineConfiguration;

    @Autowired
    private TenantProperties tenantProperties;

    @Test
    void concurrentAcquisition_onlyOneThreadWinsTheLock() throws Exception {
        startAsyncJob();

        AsyncExecutor asyncExecutor = processEngineConfiguration.getAsyncExecutor();
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_ACQUIRERS);
        try {
            List<Future<Integer>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < CONCURRENT_ACQUIRERS; i++) {
                futures.add(pool.submit(() -> {
                    startLatch.await();
                    try {
                        List<? extends JobInfoEntity> acquired =
                                managementService.executeCommand(new AcquireJobsCmd(asyncExecutor));
                        return acquired.size();
                    } catch (FlowableOptimisticLockingException ex) {
                        // Perdu la course : un autre thread a déjà verrouillé ce job entre la
                        // lecture et le flush — comportement normal, pas une erreur de test.
                        return 0;
                    }
                }));
            }
            startLatch.countDown();

            int totalAcquired = 0;
            for (Future<Integer> future : futures) {
                totalAcquired += future.get(10, TimeUnit.SECONDS);
            }

            assertThat(totalAcquired)
                    .as("un seul acquéreur concurrent doit gagner le verrou sur l'unique job disponible")
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void expiredLock_becomesReacquirableByAnotherThread() {
        String jobId = startAsyncJob();
        AsyncExecutor asyncExecutor = processEngineConfiguration.getAsyncExecutor();

        // Un premier acquéreur gagne le verrou normalement...
        List<? extends JobInfoEntity> firstAcquisition =
                managementService.executeCommand(new AcquireJobsCmd(asyncExecutor));
        assertThat(firstAcquisition).extracting(JobInfoEntity::getId).containsExactly(jobId);

        // ...puis "meurt" avant d'exécuter le job (crash simulé : verrou expiré dans le passé).
        markLockAsExpired(jobId);

        // Flowable détecte et réinitialise les verrous expirés via ResetExpiredJobsCmd — le
        // vrai mécanisme périodique (ResetExpiredJobsRunnable), pas une simulation ad hoc.
        managementService.executeCommand(new ResetExpiredJobsCmd(
                List.of(jobId),
                processEngineConfiguration.getJobServiceConfiguration().getJobEntityManager(),
                processEngineConfiguration.getJobServiceConfiguration()));

        List<? extends JobInfoEntity> reacquired =
                managementService.executeCommand(new AcquireJobsCmd(asyncExecutor));

        assertThat(reacquired)
                .as("un job dont le verrou expiré a été réinitialisé redevient acquérable")
                .extracting(JobInfoEntity::getId)
                .containsExactly(jobId);
    }

    private String startAsyncJob() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKeyAndTenantId(
                PROCESS_KEY, "lock-dimensioning-" + UUID.randomUUID(), java.util.Map.of(), tenantProperties.id());
        return managementService.createJobQuery().processInstanceId(instance.getId()).singleResult().getId();
    }

    private void markLockAsExpired(String jobId) {
        managementService.executeCommand(commandContext -> {
            JobService jobService = CommandContextUtil.getJobService(commandContext);
            JobEntity job = jobService.findJobById(jobId);
            job.setLockExpirationTime(Date.from(Instant.now().minusSeconds(3600)));
            return null;
        });
    }
}
