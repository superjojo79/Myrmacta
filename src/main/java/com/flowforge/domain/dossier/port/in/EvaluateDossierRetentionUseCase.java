package com.flowforge.domain.dossier.port.in;

/**
 * Batch retention evaluation (D-SCAN) invoked by the scheduled job. Runs without an ACL actor —
 * this is a system process, not a user-initiated mutation (D-ARCHIVE-PATH).
 */
public interface EvaluateDossierRetentionUseCase {

    EvaluationResult evaluate(String tenantId, int batchSize);

    /** {@code scanned} = dossiers read from the port (A-BATCH-COUNT) ; {@code archived} = successes. */
    record EvaluationResult(int scanned, int archived) {}
}
