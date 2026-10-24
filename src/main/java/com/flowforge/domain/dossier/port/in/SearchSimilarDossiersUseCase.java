package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.port.out.DossierSearchPort.MatchSummary;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * TODO §102 (issue #184, lot B) — brique « Rechercher des dossiers » : les dossiers ouverts du <b>même type</b> que le dossier
 * courant, autres que lui, dont les champs cherchables valent les valeurs données (D-SEARCH-BLOCK-SCOPE). Appelant système
 * (le moteur de workflow) : ne renvoie qu'un nombre et une référence, jamais une valeur d'un autre dossier (R7).
 */
public interface SearchSimilarDossiersUseCase {

    int MAX_CRITERIA = 3;

    /** @throws com.flowforge.domain.dossier.model.InvalidDossierSearchException critère hors règles (erreur de conception) */
    MatchSummary search(UUID dossierId, List<Criterion> criteria);

    /** Critère d'égalité sur un champ cherchable du type. */
    sealed interface Criterion {
        String field();

        /** Valeur prise dans un champ déclaré et non sensible du dossier courant (R8). */
        record FromDossierField(String field, String sourceField) implements Criterion {
            public FromDossierField {
                Objects.requireNonNull(field, "field");
                Objects.requireNonNull(sourceField, "sourceField");
            }
        }

        /** Valeur scalaire déjà résolue (variable de process) ; {@code null} ou vide : aucun dossier ne correspond. */
        record FromValue(String field, Object value) implements Criterion {
            public FromValue {
                Objects.requireNonNull(field, "field");
            }
        }
    }
}
