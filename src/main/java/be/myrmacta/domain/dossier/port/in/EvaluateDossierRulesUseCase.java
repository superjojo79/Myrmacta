package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TODO §80 (D1, règles) — aperçu des règles d'un type de dossier sur des valeurs en cours de saisie : champs calculés, champs
 * sans objet, champs rendus obligatoires. Sans écriture. Mêmes règles, même moteur, mêmes bornes que l'enregistrement réel
 * (le navigateur n'embarque pas de second moteur d'expressions : une seule sémantique). Les champs sensibles fournis sont
 * ignorés : une règle ne les lit jamais.
 */
public interface EvaluateDossierRulesUseCase {

    Result evaluate(String tenantId, String dossierTypeCode, Map<String, Object> fields, AccessContext ctx);

    /**
     * @param computed valeurs calculées (nom → valeur) pour les champs calculés qui en produisent une
     * @param hidden champs sans objet
     * @param requiredByRule champs rendus obligatoires par une règle
     * @param errors messages de règle en erreur (champ → message), jamais de valeur
     */
    record Result(Map<String, Object> computed, Set<String> hidden, Set<String> requiredByRule, List<RuleError> errors) {}

    record RuleError(String field, String message) {}
}
