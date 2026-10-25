package be.myrmacta.domain.dossier.port.in;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

/** Consumes {@code purge_outbox} rows (store then PG). Invoked by the scheduled job. */
public interface ProcessPurgeOutboxUseCase {

    /**
     * @param onItemStart called with the {@code dossierId} of each item right before it is purged;
     *     the returned {@link Runnable} is run once that item's processing (success or failure) is
     *     done. Lets the caller attach per-item diagnostic context (e.g. MDC) around the purge of a
     *     single item without leaking a logging dependency into this domain-pure use case.
     * @return number of rows claimed and processed (completed or failed) in this pass
     */
    int processBatch(Instant now, int limit, Function<UUID, Runnable> onItemStart);

    /**
     * Requeues rows stuck in {@code PROCESSING} (crash mid-purge, no completion/failure recorded)
     * back to {@code FAILED} so they re-enter the retry cycle on the next {@link #processBatch}.
     *
     * @return number of rows requeued
     */
    int recoverStaleProcessing(Instant olderThan);
}
