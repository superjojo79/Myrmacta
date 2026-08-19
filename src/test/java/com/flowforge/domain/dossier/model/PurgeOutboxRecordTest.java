package com.flowforge.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PurgeOutboxRecordTest {

    @Test
    void pendingAdminRecord_holdsOriginAndOptionalError() {
        UUID id = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        PurgeOutboxRecord record = PurgeOutboxRecord.pending(
                id, dossierId, "DOSSIER_PURGE_REQUESTED", PurgeOutboxOrigin.ADMIN, "{}", now);

        assertThat(record.status()).isEqualTo(PurgeOutboxStatus.PENDING);
        assertThat(record.origin()).isEqualTo(PurgeOutboxOrigin.ADMIN);
        assertThat(record.lastError()).isNull();
        assertThat(record.id()).isEqualTo(id);
        assertThat(record.dossierId()).isEqualTo(dossierId);
    }

    @Test
    void failedRecord_keepsLastError() {
        PurgeOutboxRecord record = new PurgeOutboxRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DOSSIER_PURGE_REQUESTED",
                PurgeOutboxStatus.FAILED,
                PurgeOutboxOrigin.RETENTION,
                "{}",
                Instant.parse("2026-08-19T12:00:00Z"),
                "S3 timeout");

        assertThat(record.status()).isEqualTo(PurgeOutboxStatus.FAILED);
        assertThat(record.origin()).isEqualTo(PurgeOutboxOrigin.RETENTION);
        assertThat(record.lastError()).isEqualTo("S3 timeout");
    }

    @Test
    void originIsRequired() {
        assertThatThrownBy(() -> new PurgeOutboxRecord(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "DOSSIER_PURGE_REQUESTED",
                        PurgeOutboxStatus.PENDING,
                        null,
                        "{}",
                        Instant.parse("2026-08-19T12:00:00Z"),
                        null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("origin");
    }
}
