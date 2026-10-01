package com.flowforge.infrastructure.persistence.dossier;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * TODO §81 (D2) — fonctions HQL pour la recherche sur la colonne {@code dossier.fields} (jsonb, PostgreSQL uniquement ;
 * jamais appelées sur H2, la recherche sur champ dynamique y est refusée avant la requête).
 *
 * <ul>
 *   <li>{@code flow_jsonb_contains(fields, json)} : opérateur {@code @>} — le seul que l'index GIN {@code jsonb_path_ops} sert ;</li>
 *   <li>{@code flow_jsonb_text(fields, name)} : la chaîne JSON ou {@code null} (revue D2 : un tableau ou un entier stocké sous un champ date
 *       n'entre jamais dans une comparaison de texte) ;</li>
 *   <li>{@code flow_jsonb_number(fields, name)} : le nombre ou {@code null} si la valeur n'est pas un nombre JSON — jamais de
 *       conversion qui échoue sur une ligne atypique.</li>
 * </ul>
 */
public class DossierJsonFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        var registry = functionContributions.getFunctionRegistry();
        var types = functionContributions.getTypeConfiguration().getBasicTypeRegistry();
        registry.patternDescriptorBuilder("flow_jsonb_contains", "(?1 @> cast(?2 as jsonb))")
                .setInvariantType(types.resolve(StandardBasicTypes.BOOLEAN))
                .setExactArgumentCount(2)
                .register();
        registry.patternDescriptorBuilder(
                        "flow_jsonb_text", "(case when jsonb_typeof(?1 -> ?2) = 'string' then (?1 ->> ?2) end)")
                .setInvariantType(types.resolve(StandardBasicTypes.STRING))
                .setExactArgumentCount(2)
                .register();
        registry.patternDescriptorBuilder(
                        "flow_jsonb_number",
                        "(case when jsonb_typeof(?1 -> ?2) = 'number' then cast((?1 ->> ?2) as numeric) end)")
                .setInvariantType(types.resolve(StandardBasicTypes.BIG_DECIMAL))
                .setExactArgumentCount(2)
                .register();
    }
}
