package com.flowforge.domain.dossier.port.in;

/**
 * Batch retention evaluation (D-SCAN) invoked by the scheduled job. Runs without an ACL actor —
 * this is a system process, not a user-initiated mutation (D-ARCHIVE-PATH).
 */
public interface EvaluateDossierRetentionUseCase {

    EvaluationResult evaluate(String tenantId, int batchSize);

    /**
     * {@code scanned} = dossiers read from the port (A-BATCH-COUNT) ; {@code archived} = successes.
     * {@code settingsAbsent} = D-BOOTSTRAP no-op (job should warn).
     */
    record EvaluationResult(int scanned, int archived, boolean settingsAbsent) {
        public static EvaluationResult absentSettings() {
            return new EvaluationResult(0, 0, true);
        }

        public static EvaluationResult of(int scanned, int archived) {
            return new EvaluationResult(scanned, archived, false);
        }
    }
}
