package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.dossier.DossierFieldRedactor;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.FieldDefinition;
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
    /** Profondeur max exposée (le moteur refuse 32 niveaux au total ; {@code connectorResult.json} en consomme 2). */
    static final int MAX_PARSED_DEPTH = 24;
    /** Chiffres significatifs qu'un double représente exactement : au-delà, le nombre est exposé en texte décimal exact. */
    static final int MAX_EXACT_DIGITS = 15;

    /**
     * D3b : {@code connectorResult.body} est du <b>texte</b> et le moteur ne sait pas l'analyser ({@code $eval} est interdit).
     * Quand c'est du JSON valide, borné en taille et en profondeur, on l'expose lu sous {@code connectorResult.json} ; sinon
     * la clé est omise — jamais d'échec : une réponse piégée (profondeur, nombres hors plage) ne doit pas bloquer les
     * expressions qui n'y touchent pas (revue D3b M1).
     *
     * <p>Les nombres sont lus en décimal exact : un nombre qui perdrait de la précision comme double (plus de
     * {@value #MAX_EXACT_DIGITS} chiffres significatifs ou hors plage) est exposé en <b>texte</b> décimal exact, que
     * {@code $string} restitue tel quel — un montant ne s'arrondit pas en silence (B2).
     */
    private static Object withParsedBody(Object connectorResult, ObjectMapper objectMapper) {
        if (!(connectorResult instanceof Map<?, ?> map) || !(map.get("body") instanceof String body)
                || body.isBlank() || body.length() > MAX_PARSED_BODY_CHARS || objectMapper == null) {
            return connectorResult;
        }
        try {
            Object parsed = objectMapper.readerFor(Object.class)
                    .with(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readValue(body);
            if (!(parsed instanceof Map<?, ?>) && !(parsed instanceof List<?>)) {
                return connectorResult;
            }
            Object safe = sanitize(parsed, 0);
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
            copy.put("json", safe);
            return copy;
        } catch (RuntimeException notUsable) {
            return connectorResult;
        }
    }

    private static Object sanitize(Object value, int depth) {
        if (depth > MAX_PARSED_DEPTH) {
            throw new IllegalArgumentException("too deep");
        }
        return switch (value) {
            case null -> null;
            case Map<?, ?> m -> {
                Map<String, Object> out = new LinkedHashMap<>();
                m.forEach((k, v) -> out.put(String.valueOf(k), sanitize(v, depth + 1)));
                yield out;
            }
            case List<?> l -> l.stream().map(v -> sanitize(v, depth + 1)).toList();
            case java.math.BigDecimal d -> exactNumber(d);
            case java.math.BigInteger i -> i.bitLength() <= 53 ? (Object) i.longValue() : i.toString();
            default -> value;
        };
    }

    private static Object exactNumber(java.math.BigDecimal d) {
        double asDouble = d.doubleValue();
        if (d.precision() > MAX_EXACT_DIGITS || Double.isInfinite(asDouble) || Double.isNaN(asDouble)) {
            return d.toPlainString().length() > 400 ? d.toString() : d.toPlainString();
        }
        return asDouble;
    }

    /**
     * Racine d'évaluation de l'exécution courante (dossier lu avec l'identité système, champs sensibles exclus,
     * échec fermé si le type est introuvable) — partagée par les briques « Évaluer une expression » et « Enregistrer des
     * valeurs dans le dossier ».
     */
    static Evaluated forExecution(
            org.flowable.engine.delegate.DelegateExecution execution,
            be.myrmacta.domain.dossier.port.in.GetDossierUseCase getDossier,
            be.myrmacta.domain.dossier.port.out.DossierTypeRepository dossierTypes,
            be.myrmacta.infrastructure.config.TenantProperties tenantProperties,
            ObjectMapper objectMapper) {
        Dossier dossier = null;
        List<FieldDefinition> definitions = List.of();
        Object dossierId = execution.getVariable("dossierId");
        if (dossierId != null) {
            dossier = getDossier.get(
                    UUID.fromString(dossierId.toString()),
                    be.myrmacta.domain.dossier.model.AccessContext.system("flowable-expression"));
            // Échec FERMÉ : sans la définition du type on ne sait pas quels champs sont sensibles.
            String typeCode = dossier.dossierTypeCode();
            definitions = dossierTypes.findByCode(tenantProperties.id(), typeCode)
                    .map(be.myrmacta.domain.dossier.model.DossierType::fieldDefinitions)
                    .orElseThrow(() -> new IllegalStateException(
                            "dossier type " + typeCode + " not found: cannot tell which fields are sensitive"));
        }
        Map<String, Object> context = build(
                dossier, definitions, execution.getVariable("connectorResult"), execution.getVariables(), objectMapper);
        return new Evaluated(dossier, definitions, context);
    }

    record Evaluated(Dossier dossier, List<FieldDefinition> definitions, Map<String, Object> context) {}

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
