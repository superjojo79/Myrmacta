package com.flowforge.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.support.PostgresTestContainerSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link SequentialReferenceGenerator} sous accès concurrent multi-pods réel — fiabilisation-tests-plateforme,
 * TODO §26 reste, D-TESTCONTAINERS (adoption ciblée). Chaque "pod" a sa propre
 * {@link DataSource}/{@link JdbcTemplate}/transaction, comme en déploiement réel (plusieurs
 * répliques d'une même instance tenant partageant un schéma, {@code 02-multi-tenant.md}), et
 * chaque appel est enveloppé dans une transaction réelle ({@link TransactionTemplate}) pour
 * reproduire fidèlement le {@code @Transactional} de production — sans quoi les 3 instructions
 * SQL de {@code generate()} (INSERT, UPDATE, SELECT) tourneraient en 3 auto-commits indépendants
 * et pourraient lire la valeur d'un autre pod.
 *
 * <p><strong>⚠️ Non exécuté dans ce bac à sable</strong> (Docker absent, D-TESTCONTAINERS-EXEC) —
 * écrit et compilé ; exécution/vérification à faire en CI (GitHub Actions {@code ubuntu-latest}).
 */
class SequentialReferenceMultiPodIT {

    private static final int PODS = 8;
    private static final int CALLS_PER_POD = 10;
    private static final Pattern REFERENCE_PATTERN = Pattern.compile("DOS-SINISTRE-\\d{4}-(\\d{6})");

    @Test
    void concurrentMultiPodGeneration_hasNoDuplicateAndNoGap() throws Exception {
        Assumptions.assumeTrue(
                PostgresTestContainerSupport.isDockerAvailable(),
                "Docker indisponible dans ce bac à sable (D-TESTCONTAINERS-EXEC) — exécuté en CI (ubuntu-latest, Docker natif)");

        String schema = "seq_multipod_" + UUID.randomUUID().toString().replace("-", "");
        PostgresTestContainerSupport.createSchema(schema);
        PostgresTestContainerSupport.migrateSchema(schema);

        try {
            List<TransactionTemplate> pods = new ArrayList<>();
            List<SequentialReferenceGenerator> generators = new ArrayList<>();
            Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
            for (int i = 0; i < PODS; i++) {
                DataSource dataSource = PostgresTestContainerSupport.dataSourceForSchema(schema);
                JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
                pods.add(new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
                generators.add(new SequentialReferenceGenerator(jdbcTemplate, fixedClock));
            }

            CountDownLatch startLatch = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(PODS);
            try {
                List<Future<String>> futures = new ArrayList<>();
                for (int pod = 0; pod < PODS; pod++) {
                    TransactionTemplate txTemplate = pods.get(pod);
                    SequentialReferenceGenerator generator = generators.get(pod);
                    for (int call = 0; call < CALLS_PER_POD; call++) {
                        futures.add(pool.submit(() -> {
                            startLatch.await();
                            return txTemplate.execute(status -> generator.generate("tenant_dev", "SINISTRE"));
                        }));
                    }
                }
                startLatch.countDown();

                List<String> references = new ArrayList<>();
                for (Future<String> future : futures) {
                    references.add(future.get(20, TimeUnit.SECONDS));
                }

                List<Integer> sequences = references.stream().map(this::extractSequence).toList();
                Set<Integer> distinctSequences = Set.copyOf(sequences);

                assertThat(references).hasSize(PODS * CALLS_PER_POD);
                assertThat(distinctSequences)
                        .as("aucun doublon dans les séquences générées par les %d pods", PODS)
                        .hasSize(PODS * CALLS_PER_POD);
                assertThat(distinctSequences)
                        .as("aucun trou : les séquences doivent couvrir exactement 1..%d", PODS * CALLS_PER_POD)
                        .isEqualTo(java.util.stream.IntStream.rangeClosed(1, PODS * CALLS_PER_POD)
                                .boxed()
                                .collect(Collectors.toSet()));
            } finally {
                pool.shutdownNow();
            }
        } finally {
            PostgresTestContainerSupport.dropSchema(schema);
        }
    }

    private int extractSequence(String reference) {
        Matcher matcher = REFERENCE_PATTERN.matcher(reference);
        if (!matcher.matches()) {
            throw new IllegalStateException("Unexpected reference format: " + reference);
        }
        return Integer.parseInt(matcher.group(1));
    }
}
