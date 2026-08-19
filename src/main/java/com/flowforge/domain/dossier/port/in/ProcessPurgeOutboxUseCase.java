package com.flowforge.domain.dossier.port.in;

import java.time.Instant;

/** Consumes {@code purge_outbox} rows (store then PG). Invoked by the scheduled job. */
public interface ProcessPurgeOutboxUseCase {

    /** @return number of rows claimed and processed (completed or failed) in this pass */
    int processBatch(Instant now, int limit);
}
