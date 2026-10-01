package com.flowforge.domain.dossier.model;

/** Lecture des critères {@code f.*} partagée par l'API REST et l'outil MCP. */
public final class FieldConditionParser {

    private FieldConditionParser() {}

    /**
     * TODO §81 (D2) — critères {@code f.<champ>=v} (égalité), {@code f.<champ>.in=a,b}, {@code .gte}, {@code .lte},
     * {@code .prefix}. Les noms de champ ne contiennent pas de point : le premier point après {@code f.} sépare l'opérateur.
     * Ordre déterministe (par clé) ; un opérateur inconnu est refusé (400), jamais ignoré.
     */
    public static java.util.List<FieldCondition> parse(
            java.util.Map<String, String> params) {
        java.util.List<FieldCondition> conditions = new java.util.ArrayList<>();
        new java.util.TreeMap<>(params).forEach((key, raw) -> {
            if (!key.startsWith("f.")) {
                return;
            }
            String rest = key.substring(2);
            int dot = rest.indexOf('.');
            String field = dot < 0 ? rest : rest.substring(0, dot);
            String opName = dot < 0 ? "eq" : rest.substring(dot + 1);
            FieldCondition.Op op = switch (opName) {
                case "eq" -> FieldCondition.Op.EQ;
                case "in" -> FieldCondition.Op.IN;
                case "gte" -> FieldCondition.Op.GTE;
                case "lte" -> FieldCondition.Op.LTE;
                case "prefix" -> FieldCondition.Op.PREFIX;
                default -> throw new InvalidDossierSearchException(
                        "unknown filter operator '" + opName + "' on '" + key + "' (eq, in, gte, lte, prefix)");
            };
            if (raw == null || raw.isBlank()) {
                throw new InvalidDossierSearchException("filter '" + key + "' has no value");
            }
            java.util.List<String> values = op == FieldCondition.Op.IN
                    ? java.util.Arrays.stream(raw.split(",", -1)).toList()
                    : java.util.List.of(raw);
            conditions.add(new FieldCondition(field, op, values));
        });
        return conditions;
    }
}
