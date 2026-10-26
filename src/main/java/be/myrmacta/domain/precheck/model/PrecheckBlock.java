package be.myrmacta.domain.precheck.model;

import java.util.Objects;

/** TODO §106 (lot B) — une brique « Pré-vérification IA » d'un BPMN, avec sa spécification validée. */
public record PrecheckBlock(String activityId, PrecheckSpec spec) {
    public PrecheckBlock {
        Objects.requireNonNull(activityId, "activityId");
        Objects.requireNonNull(spec, "spec");
    }
}
