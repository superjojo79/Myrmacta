package be.myrmacta.domain.promotion.artifact;

import java.util.Objects;

/**
 * TODO §117 — artefacts de la tranche 2 de la promotion, regroupés pour ne pas allonger encore les constructeurs de l'export et de
 * l'import. Chacun porte ses propres dépendances.
 */
public record AdditionalArtifacts(
        UserGroupArtifacts groups, GroupReferenceCheck groupReferences, TypeSettingsArtifacts typeSettings) {

    public AdditionalArtifacts {
        Objects.requireNonNull(groups, "groups");
        Objects.requireNonNull(groupReferences, "groupReferences");
        Objects.requireNonNull(typeSettings, "typeSettings");
    }
}
