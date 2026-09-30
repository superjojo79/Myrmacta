package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.dossier.DossierFieldRedactor;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.FieldDefinition;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TODO §75 (LC2, D-EXPR-CONTEXT) — document racine exposé à une expression métier exécutée dans un process :
 * <ul>
 *   <li>{@code dossier} : référence, type, statut, libellé et champs <b>déclarés et non sensibles</b> — liste blanche :
 *       un champ sensible, ou stocké mais non déclaré (renommé, supprimé, type autorisant les champs inconnus), n'est
 *       pas exposé ; une expression ne peut pas en recopier la valeur vers une variable, un message ou un champ lisible
 *       par un non-administrateur ;</li>
 *   <li>{@code connectorResult} : résultat du dernier appel de connecteur, tel quel (JSON) ;</li>
 *   <li>{@code variables} : variables de process scalaires (texte, nombre, booléen, date, UUID).</li>
 * </ul>
 * {@code now} est ajouté par le moteur.
 */
final class BusinessExpressionContext {

    private BusinessExpressionContext() {}

    static Map<String, Object> build(
            Dossier dossier,
            List<FieldDefinition> fieldDefinitions,
            Object connectorResult,
            Map<String, Object> processVariables) {
        Map<String, Object> root = new LinkedHashMap<>();
        if (dossier != null) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("reference", dossier.reference());
            d.put("type", dossier.dossierTypeCode());
            d.put("status", dossier.status() == null ? null : dossier.status().name());
            d.put("label", dossier.label());
            d.put("fields", DossierFieldRedactor.declaredNonSensitiveFields(dossier, fieldDefinitions));
            root.put("dossier", d);
        }
        if (connectorResult != null) {
            root.put("connectorResult", connectorResult);
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        processVariables.forEach((name, value) -> {
            Object scalar = scalar(value);
            if (scalar != null) {
                variables.put(name, scalar);
            }
        });
        root.put("variables", variables);
        return root;
    }

    private static Object scalar(Object value) {
        return switch (value) {
            case null -> null;
            case String s -> s;
            case Boolean b -> b;
            case Number n -> n;
            case UUID u -> u.toString();
            case Instant i -> i.toString();
            case LocalDate d -> d.toString();
            case LocalDateTime d -> d.toString();
            case OffsetDateTime d -> d.toString();
            case java.util.Date d -> d.toInstant().toString();
            default -> null;
        };
    }
}
