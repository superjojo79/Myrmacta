package com.flowforge.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.dossier.port.out.PurgeOutboxRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Recovery des lignes {@code purge_outbox} bloquées en {@code PROCESSING} (crash mi-purge, ni
 * complétion ni échec enregistré — jamais reprises par {@code findRetryable} sinon).
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class JpaPurgeOutboxRepositoryAdapterIT {

    @Autowired
    private PurgeOutboxRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void requeueStaleProcessing_movesOldProcessingRowsToFailed_leavesRecentOnesAlone() {
        UUID staleId = UUID.randomUUID();
        UUID freshId = UUID.randomUUID();
        insertProcessingRow(staleId, Instant.now().minus(Duration.ofHours(3)));
        insertProcessingRow(freshId, Instant.now());

        int recovered = repository.requeueStaleProcessing(Instant.now().minus(Duration.ofHours(2)));

        assertThat(recovered).isEqualTo(1);
        assertThat(statusOf(staleId)).isEqualTo("FAILED");
        assertThat(statusOf(freshId)).isEqualTo("PROCESSING");
    }

    private void insertProcessingRow(UUID id, Instant updatedAt) {
        jdbc.update(
                """
                INSERT INTO purge_outbox
                    (id, dossier_id, event_type, status, origin, payload_json, created_at, updated_at)
                VALUES (?, ?, 'DOSSIER_PURGE_REQUESTED', 'PROCESSING', 'ADMIN', '{}', ?, ?)
                """,
                id,
                UUID.randomUUID(),
                java.sql.Timestamp.from(updatedAt),
                java.sql.Timestamp.from(updatedAt));
    }

    private String statusOf(UUID id) {
        return jdbc.queryForObject("SELECT status FROM purge_outbox WHERE id = ?", String.class, id);
    }
}
