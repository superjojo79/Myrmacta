package be.myrmacta.domain.precheck.model;

import java.util.List;

/**
 * TODO §106 (lot B, HC3) — réponse du modèle telle que lue par l'adaptateur JSON, <b>non encore validée</b> : les valeurs
 * sont des chaînes ou {@code null} ; le domaine vérifie types, sources et longueurs.
 */
public record ModelOutput(String summary, List<Item> findings) {

    public record Item(String kind, String sourceType, String sourceRef, String message, Double confidence) {}

    public ModelOutput {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
