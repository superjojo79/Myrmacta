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
import tools.jackson.databind.ObjectMapper;

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
        return build(dossier, fieldDefinitions, connectorResult, processVariables, null);
    }

    static Map<String, Object> build(
            Dossier dossier,
            List<FieldDefinition> fieldDefinitions,
            Object connectorResult,
            Map<String, Object> processVariables,
            ObjectMapper objectMapper) {
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
            root.put("connectorResult", withParsedBody(connectorResult, objectMapper));
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

    /** Au-delà, le corps n'est pas analysé : une réponse volumineuse ne doit pas faire échouer toute expression. */
    static final int MAX_PARSED_BODY_CHARS = 256 * 1024;

    /**
     * D3b : {@code connectorResult.body} est du <b>texte</b> et le moteur ne sait pas l'analyser ({@code $eval} est interdit).
     * Quand c'est du JSON valide et de taille raisonnable, on l'expose lu sous {@code connectorResult.json}.
     */
    private static Object withParsedBody(Object connectorResult, ObjectMapper objectMapper) {
        if (!(connectorResult instanceof Map<?, ?> map) || !(map.get("body") instanceof String body)
                || body.isBlank() || body.length() > MAX_PARSED_BODY_CHARS || objectMapper == null) {
            return connectorResult;
        }
        try {
            Object parsed = objectMapper.readValue(body, Object.class);
            if (!(parsed instanceof Map<?, ?>) && !(parsed instanceof List<?>)) {
                return connectorResult;
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
            copy.put("json", parsed);
            return copy;
        } catch (RuntimeException notJson) {
            return connectorResult;
        }
    }

    /**
     * Racine d'évaluation de l'exécution courante (dossier lu avec l'identité système, champs sensibles exclus,
     * échec fermé si le type est introuvable) — partagée par les briques « Évaluer une expression » et « Enregistrer des
     * valeurs dans le dossier ».
     */
    static Evaluated forExecution(
            org.flowable.engine.delegate.DelegateExecution execution,
            com.flowforge.domain.dossier.port.in.GetDossierUseCase getDossier,
            com.flowforge.domain.dossier.port.out.DossierTypeRepository dossierTypes,
            com.flowforge.infrastructure.config.TenantProperties tenantProperties,
            ObjectMapper objectMapper) {
        Dossier dossier = null;
        List<FieldDefinition> definitions = List.of();
        Object dossierId = execution.getVariable("dossierId");
        if (dossierId != null) {
            dossier = getDossier.get(
                    UUID.fromString(dossierId.toString()),
                    com.flowforge.domain.dossier.model.AccessContext.system("flowable-expression"));
            // Échec FERMÉ : sans la définition du type on ne sait pas quels champs sont sensibles.
            String typeCode = dossier.dossierTypeCode();
            definitions = dossierTypes.findByCode(tenantProperties.id(), typeCode)
                    .map(com.flowforge.domain.dossier.model.DossierType::fieldDefinitions)
                    .orElseThrow(() -> new IllegalStateException(
                            "dossier type " + typeCode + " not found: cannot tell which fields are sensitive"));
        }
        Map<String, Object> context = build(
                dossier, definitions, execution.getVariable("connectorResult"), execution.getVariables(), objectMapper);
        return new Evaluated(dossier, context);
    }

    record Evaluated(Dossier dossier, Map<String, Object> context) {}

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
