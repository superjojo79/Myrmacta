package be.myrmacta.domain.workflow.model;

import java.util.Set;

/**
 * Règle D-HISTORY-SIGNIFICANT (TODO §131) : ce qui peut apparaître dans l'historique métier
 * d'une exécution ({@code GET /api/workflow-links/{linkId}/history}).
 */
public final class SignificantActivityFilter {

    /**
     * Borne de lecture Flowable (activités brutes, ordre chronologique) avant filtre — même esprit
     * que {@code D-INCIDENT-SCAN} (TODO §94) : pas de {@code .list()} non borné (§24).
     */
    public static final int RAW_SCAN_LIMIT = 2_000;

    private static final Set<String> TECHNICAL_TYPES = Set.of(
            "sequenceFlow",
            "exclusiveGateway",
            "inclusiveGateway",
            "parallelGateway",
            "eventBasedGateway",
            "complexGateway");

    private SignificantActivityFilter() {}

    public static boolean isSignificant(String activityType, String activityName) {
        if (activityType != null && TECHNICAL_TYPES.contains(activityType)) {
            return false;
        }
        return activityName != null && !activityName.isBlank();
    }
}
