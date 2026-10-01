package com.flowforge.infrastructure.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.reporting.model.SlaAlertState;
import com.flowforge.domain.reporting.model.SlaStatus;
import com.flowforge.domain.reporting.model.TenantSlaSettings;
import com.flowforge.domain.reporting.port.out.SlaAlertStateRepository;
import com.flowforge.domain.reporting.port.out.TenantSlaSettingsRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * D5 (TODO §79) — deux exécutants du scan d'alertes SLA qui voient la même transition (ON_TRACK → BREACHED) ne mettent
 * l'alerte en file qu'une fois : la transition d'état est un compare-and-set pris avant la notification.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class SlaAlertConcurrencyIT {

    private static final String TENANT = "tenant_dev";

    @Autowired
    private SlaAlertJob job;

    @Autowired
    private TenantSlaSettingsRepository settingsRepository;

    @Autowired
    private DossierTypeRepository dossierTypes;

    @Autowired
    private DossierRepository dossiers;

    @Autowired
    private SlaAlertStateRepository states;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void twoConcurrentScansEnqueueTheTransitionAlertExactlyOnce() throws Exception {
        String code = "SLACONC" + (System.nanoTime() % 100_000);
        dossierTypes.save(new DossierType(UUID.randomUUID(), TENANT, code, "SLA concurrence", "wf", true, true, List.of(),
                null, false, 1, "test", "test"));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String reference = "SLAC-" + System.nanoTime();
        Dossier dossier = dossiers.save(new Dossier(UUID.randomUUID(), TENANT, reference, code, "Dossier en retard",
                DossierStatus.OUVERT, Map.of(), null, 0L, "test", now.minus(10, ChronoUnit.HOURS), null,
                now.minus(10, ChronoUnit.HOURS), null, null, null));
        states.save(new SlaAlertState(TENANT, dossier.id(), SlaStatus.ON_TRACK, now.minus(2, ChronoUnit.HOURS)));
        TenantSlaSettings before = settingsRepository.findByTenantId(TENANT).orElse(null);
        settingsRepository.save(new TenantSlaSettings(TENANT, 95, true, 15, List.of("alerte@example.com"), List.of(), false, 24,
                30, now.minus(1, ChronoUnit.DAYS), null, null, null, now, "test"));
        try {
            int scanners = 4;
            ExecutorService pool = Executors.newFixedThreadPool(scanners);
            CountDownLatch go = new CountDownLatch(1);
            try {
                List<Future<?>> futures = new java.util.ArrayList<>();
                for (int i = 0; i < scanners; i++) {
                    futures.add(pool.submit(() -> {
                        go.await();
                        job.process(now);
                        return null;
                    }));
                }
                go.countDown();
                for (Future<?> f : futures) {
                    f.get();
                }
            } finally {
                pool.shutdownNow();
            }

            Integer enqueued = jdbc.queryForObject(
                    "select count(*) from notification_outbox where cast(payload_json as varchar) like ?", Integer.class,
                    "%" + reference + "%");
            assertThat(enqueued).as("une seule alerte pour la transition, malgré %d exécutants", scanners).isEqualTo(1);
            assertThat(states.find(TENANT, dossier.id())).get().extracting(SlaAlertState::lastStatus).isEqualTo(SlaStatus.BREACHED);
        } finally {
            // Ne laisse pas l'alerte active pour le reste de la suite.
            settingsRepository.save(new TenantSlaSettings(TENANT, 95, false, 60, List.of(), List.of(), false, 24, 30, null,
                    null, null, null, now, "test"));
            if (before != null && before.alertingEnabled()) {
                settingsRepository.save(before);
            }
        }
    }
}
