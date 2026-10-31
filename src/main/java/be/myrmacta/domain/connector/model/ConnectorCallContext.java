package be.myrmacta.domain.connector.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Issue #205 (TODO §101, D-STORAGE-REF-DOSSIER) — d'où vient un appel de connecteur SERVICE. Un appel direct (REST
 * {@code TENANT_ADMIN} ou M2M {@code API_SERVICE}) n'a pas de dossier ; un appel depuis un process porte le dossier de
 * l'exécution, lu dans la variable plateforme {@code dossierId} (réservée, ni posable à une complétion ni réécrite par
 * le BPMN : TODO §97 et §99). {@code dossierId} vaut {@code null} pour un process sans dossier exploitable.
 */
public record ConnectorCallContext(Origin origin, UUID dossierId, boolean requestBuiltByProcess) {

    public enum Origin { DIRECT, PROCESS }

    /**
     * @param requestBuiltByProcess TODO §112 (revue M4) : l'entrée vient de l'attribut littéral {@code connectorRequest}
     *     du BPMN déployé, et non de la variable {@code connectorInput} qu'un utilisateur peut poser à une complétion
     */
    public ConnectorCallContext {
        Objects.requireNonNull(origin, "origin");
        if (origin == Origin.DIRECT && (dossierId != null || requestBuiltByProcess)) {
            throw new IllegalArgumentException("a direct connector call has no dossier and no process-built request");
        }
    }

    public ConnectorCallContext(Origin origin, UUID dossierId) {
        this(origin, dossierId, false);
    }

    public static ConnectorCallContext direct() {
        return new ConnectorCallContext(Origin.DIRECT, null, false);
    }

    public static ConnectorCallContext process(UUID dossierId) {
        return new ConnectorCallContext(Origin.PROCESS, dossierId, false);
    }

    /** Appel depuis un process dont l'entrée a été construite par {@code flowable:connectorRequest} (TODO §112). */
    public static ConnectorCallContext processWithBuiltRequest(UUID dossierId) {
        return new ConnectorCallContext(Origin.PROCESS, dossierId, true);
    }

    public boolean fromProcess() {
        return origin == Origin.PROCESS;
    }
}
