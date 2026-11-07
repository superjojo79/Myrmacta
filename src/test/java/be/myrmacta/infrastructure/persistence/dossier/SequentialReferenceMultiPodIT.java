package be.myrmacta.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.support.OidcClientTestConfiguration;
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
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link SequentialReferenceGenerator} sous accès concurrent — fiabilisation-tests-plateforme, TODO §26 reste.
 * Chaque appel tourne dans sa propre transaction et sa propre connexion (comme plusieurs répliques d'un même tenant,
 * {@code 02-multi-tenant.md}) : l'incrément atomique ne doit produire ni doublon ni trou.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class SequentialReferenceMultiPodIT {

    private static final int THREADS = 8;
    private static final int CALLS = 80;
    private static final Pattern REFERENCE = Pattern.compile("DOS-([A-Z0-9]+)-\\d{4}-(\\d{6})");

    @Autowired private SequentialReferenceGenerator generator;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void concurrentGeneration_hasNoDuplicateAndNoGap() throws Exception {
        String type = "SEQ" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < CALLS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return tx.execute(status -> generator.generate("tenant_dev", type));
                }));
            }
            start.countDown();
            List<Integer> sequences = new ArrayList<>();
            for (Future<String> future : futures) {
                Matcher matcher = REFERENCE.matcher(future.get(30, TimeUnit.SECONDS));
                assertThat(matcher.matches()).isTrue();
                sequences.add(Integer.parseInt(matcher.group(2)));
            }

            assertThat(Set.copyOf(sequences)).as("aucun doublon").hasSize(CALLS);
            assertThat(Set.copyOf(sequences)).as("aucun trou : 1..%d", CALLS)
                    .isEqualTo(IntStream.rangeClosed(1, CALLS).boxed().collect(Collectors.toSet()));
        } finally {
            pool.shutdownNow();
        }
    }
}
