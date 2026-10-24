package com.flowforge.domain.connector.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Issue #205 (TODO §101, D-STORAGE-REF-DOSSIER) — d'où vient un appel de connecteur SERVICE. Un appel direct (REST
 * {@code TENANT_ADMIN} ou M2M {@code API_SERVICE}) n'a pas de dossier ; un appel depuis un process porte le dossier de
 * l'exécution, lu dans la variable plateforme {@code dossierId} (réservée, ni posable à une complétion ni réécrite par
 * le BPMN : TODO §97 et §99). {@code dossierId} vaut {@code null} pour un process sans dossier exploitable.
 */
public record ConnectorCallContext(Origin origin, UUID dossierId) {

    public enum Origin { DIRECT, PROCESS }

    public ConnectorCallContext {
        Objects.requireNonNull(origin, "origin");
        if (origin == Origin.DIRECT && dossierId != null) {
            throw new IllegalArgumentException("a direct connector call has no dossier");
        }
    }

    public static ConnectorCallContext direct() {
        return new ConnectorCallContext(Origin.DIRECT, null);
    }

    public static ConnectorCallContext process(UUID dossierId) {
        return new ConnectorCallContext(Origin.PROCESS, dossierId);
    }

    public boolean fromProcess() {
        return origin == Origin.PROCESS;
    }
}
