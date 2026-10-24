package com.flowforge.domain.dossier;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.UniqueDuplicate;
import com.flowforge.domain.dossier.model.UniqueFieldKeys;
import com.flowforge.domain.dossier.model.UniqueKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * TODO §102 (issue #184) — clés d'unicité d'un dossier <b>tel qu'il est selon son type</b> : les champs calculés sont
 * recalculés par les règles avant d'en tirer les clés. Sans cela, un champ calculé ajouté au type après coup (absent des
 * valeurs stockées) ou dont la formule a changé produirait une clé fausse, et un simple changement de statut effacerait la
 * protection posée à la reconstruction.
 *
 * <p>Lit {@code Dossier.fields()} (liste blanche ArchUnit) : les règles ne lisent que des champs non sensibles, un champ
 * unique n'est jamais sensible, et aucune valeur n'est renvoyée à un navigateur.
 */
public final class DossierUniqueKeys {

    private final DossierFieldRules rules;

    public DossierUniqueKeys(DossierFieldRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    public Set<UniqueKey> of(List<FieldDefinition> definitions, Dossier dossier) {
        Objects.requireNonNull(dossier, "dossier");
        if (!UniqueFieldKeys.hasUniqueField(definitions) || dossier.status().blocksMutation()) {
            return Set.of();
        }
        Map<String, Object> fields = rules.apply(definitions, dossier.fields(), Set.of()).fields();
        return UniqueFieldKeys.of(definitions, dossier.status(), fields);
    }

    /** Valeurs tenues par plus d'un des dossiers donnés (les clôturés ne tiennent rien), triées par champ puis valeur. */
    public List<UniqueDuplicate> duplicates(List<FieldDefinition> definitions, List<Dossier> dossiers) {
        if (!UniqueFieldKeys.hasUniqueField(definitions)) {
            return List.of();
        }
        Map<UniqueKey, List<String>> holders = new LinkedHashMap<>();
        for (Dossier dossier : dossiers) {
            for (UniqueKey key : of(definitions, dossier)) {
                holders.computeIfAbsent(key, k -> new ArrayList<>()).add(dossier.reference());
            }
        }
        return holders.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> new UniqueDuplicate(
                        entry.getKey().field(), entry.getKey().value(), entry.getValue().stream().sorted().toList()))
                .sorted(Comparator.comparing(UniqueDuplicate::field).thenComparing(UniqueDuplicate::value))
                .toList();
    }
}
