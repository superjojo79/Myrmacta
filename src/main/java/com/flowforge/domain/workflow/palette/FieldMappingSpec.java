package com.flowforge.domain.workflow.palette;

import com.flowforge.domain.workflow.model.InvalidBpmnException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO §78 (D3b) — forme textuelle du paramètre {@code fieldMapping} de la brique « Enregistrer des valeurs dans le
 * dossier » : une ligne {@code champ = expression} par champ cible, l'expression étant du JSONata borné (LC2).
 *
 * <p>Format de lignes plutôt que JSON : une expression contient des guillemets, qu'un JSON imbriqué dans un attribut XML
 * rendrait illisibles ; une ligne par champ se relit et se compare facilement. Le séparateur est le <b>premier</b>
 * {@code =} de la ligne (un nom de champ n'en contient pas ; l'expression peut contenir {@code =} et {@code ==}).
 */
public final class FieldMappingSpec {

    public static final int MAX_ENTRIES = 20;
    public static final int MAX_EXPRESSION_CHARS = 2000;

    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,59}");
    private static final Pattern LINE = Pattern.compile("^([^=]*?)\\s*=(?!=)\\s*(.*)$");

    public record Entry(String field, String expression) {}

    private FieldMappingSpec() {}

    /** @throws InvalidBpmnException forme invalide (aucun champ, doublon, nom ou expression hors règles) */
    public static List<Entry> parse(String raw, String owner) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidBpmnException(owner + " must list at least one 'field = expression' line");
        }
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String line : raw.split("\\R")) {
            String stripped = line.strip();
            if (stripped.isEmpty()) {
                continue;
            }
            Matcher m = LINE.matcher(stripped);
            if (!m.matches()) {
                throw new InvalidBpmnException(owner + ": each line must be 'field = expression'");
            }
            String field = m.group(1).strip();
            String expression = m.group(2).strip();
            if (!FIELD_NAME.matcher(field).matches()) {
                throw new InvalidBpmnException(owner + ": '" + abbreviate(field)
                        + "' is not a valid field name (letters, digits, _; starts with a letter)");
            }
            if (expression.isEmpty()) {
                throw new InvalidBpmnException(owner + ": field '" + field + "' has no expression");
            }
            if (expression.length() > MAX_EXPRESSION_CHARS) {
                throw new InvalidBpmnException(
                        owner + ": the expression of '" + field + "' exceeds " + MAX_EXPRESSION_CHARS + " characters");
            }
            if (!seen.add(field)) {
                throw new InvalidBpmnException(owner + ": field '" + field + "' is mapped twice");
            }
            entries.add(new Entry(field, expression));
            if (entries.size() > MAX_ENTRIES) {
                throw new InvalidBpmnException(owner + ": at most " + MAX_ENTRIES + " fields can be mapped");
            }
        }
        if (entries.isEmpty()) {
            throw new InvalidBpmnException(owner + " must list at least one 'field = expression' line");
        }
        return List.copyOf(entries);
    }

    private static String abbreviate(String value) {
        return value.length() <= 30 ? value : value.substring(0, 30) + "…";
    }
}
