package be.myrmacta.infrastructure.persistence.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.reporting.model.BiExportFile;
import be.myrmacta.domain.reporting.model.BiExportKind;
import be.myrmacta.domain.reporting.model.TenantSlaSettings;
import be.myrmacta.domain.reporting.port.out.BiExportAlreadyCommittedException;
import be.myrmacta.domain.reporting.port.out.BiExportCommitPort;
import be.myrmacta.domain.reporting.port.out.TenantSlaSettingsRepository;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
 * D5 (TODO §79) — deux exécutants qui exportent le même créneau BI : le commit avance l'horloge par compare-and-set ; un seul
 * catalogue de fichiers est enregistré, l'autre commit est refusé sans rien écrire.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class BiExportCommitConcurrencyIT {

    private static final String TENANT = "tenant_dev";

    @Autowired
    private BiExportCommitPort commitPort;

    @Autowired
    private TenantSlaSettingsRepository settingsRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private static TenantSlaSettings settings(Instant enabledAt, Instant lastExport) {
        return new TenantSlaSettings(TENANT, 95, false, 60, List.of(), List.of(), true, 24, 30, null, null, enabledAt,
                lastExport, Instant.now(), "test");
    }

    @Test
    void onlyOneOfTheConcurrentCommitsForTheSameSlotCataloguesFiles() throws Exception {
        TenantSlaSettings before = settingsRepository.findByTenantId(TENANT).orElse(null);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant observed = now.minus(30, ChronoUnit.HOURS);
        settingsRepository.save(settings(observed, observed));
        String marker = "bi-conc-" + System.nanoTime() + ".csv";
        try {
            int executors = 6;
            ExecutorService pool = Executors.newFixedThreadPool(executors);
            CountDownLatch go = new CountDownLatch(1);
            try {
                List<Future<Boolean>> futures = new ArrayList<>();
                for (int i = 0; i < executors; i++) {
                    futures.add(pool.submit(() -> {
                        go.await();
                        BiExportFile file = new BiExportFile(UUID.randomUUID(), TENANT, BiExportKind.SLA, null,
                                "ref-" + UUID.randomUUID(), marker, now, 3);
                        try {
                            commitPort.commitSuccessfulExport(TENANT, List.of(file), now, observed);
                            return true;
                        } catch (BiExportAlreadyCommittedException loser) {
                            return false;
                        }
                    }));
                }
                go.countDown();
                long winners = 0;
                for (Future<Boolean> f : futures) {
                    winners += f.get() ? 1 : 0;
                }
                assertThat(winners).as("un seul commit pour le créneau").isEqualTo(1);
            } finally {
                pool.shutdownNow();
            }
            Integer rows = jdbc.queryForObject("select count(*) from bi_export_file where file_name = ?", Integer.class, marker);
            assertThat(rows).as("un seul jeu de fichiers catalogué").isEqualTo(1);
            assertThat(settingsRepository.findByTenantId(TENANT)).get()
                    .extracting(TenantSlaSettings::lastBiExportAt).isEqualTo(now);
        } finally {
            jdbc.update("delete from bi_export_file where file_name = ?", marker);
            settingsRepository.save(before != null ? before : settings(null, null));
        }
    }

    @Test
    void aFirstEverExportIsAlsoACompareAndSet() {
        TenantSlaSettings before = settingsRepository.findByTenantId(TENANT).orElse(null);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        settingsRepository.save(settings(now.minus(2, ChronoUnit.DAYS), null));
        try {
            commitPort.commitSuccessfulExport(TENANT, List.of(), now, null);
            assertThatThrownBy(() -> commitPort.commitSuccessfulExport(TENANT, List.of(), now.plusSeconds(1), null))
                    .isInstanceOf(BiExportAlreadyCommittedException.class);
        } finally {
            settingsRepository.save(before != null ? before : settings(null, null));
        }
    }
}
