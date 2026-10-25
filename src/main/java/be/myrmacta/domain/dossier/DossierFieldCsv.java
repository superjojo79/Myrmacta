package be.myrmacta.domain.dossier;

import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TODO §76 (D-CHAMPS-EXPORT) — colonnes et cellules CSV d'un champ, partagées par l'export des dossiers et l'export BI :
 * un montant s'aplatit en {@code <nom>.amount} / {@code <nom>.currency} (montant décimal exact en texte, devise à part), une
 * référence en {@code <nom>.system} / {@code .id} / {@code .label}, un groupe répétable en <b>une</b> colonne portant les
 * lignes en JSON compact (le nombre de colonnes reste stable quel que soit le nombre de lignes). Les champs sensibles sont
 * exclus en amont par les appelants.
 */
public final class DossierFieldCsv {

    private DossierFieldCsv() {}

    public static List<String> headers(FieldDefinition definition) {
        String name = definition.name();
        return switch (definition.type()) {
            case MONEY -> List.of(name + ".amount", name + ".currency");
            case EXTERNAL_REF -> List.of(name + ".system", name + ".id", name + ".label");
            default -> List.of(name);
        };
    }

    public static List<String> cells(FieldDefinition definition, Object value) {
        int width = headers(definition).size();
        List<String> cells = new ArrayList<>(width);
        if (value == null) {
            for (int i = 0; i < width; i++) {
                cells.add("");
            }
            return cells;
        }
        switch (definition.type()) {
            case MONEY -> {
                Map<?, ?> m = value instanceof Map<?, ?> map ? map : Map.of();
                cells.add(text(m.get("amount")));
                cells.add(text(m.get("currency")));
            }
            case EXTERNAL_REF -> {
                Map<?, ?> m = value instanceof Map<?, ?> map ? map : Map.of();
                cells.add(text(m.get("system")));
                cells.add(text(m.get("id")));
                cells.add(text(m.get("label")));
            }
            default -> cells.add(definition.type() == FieldType.REPEATABLE_GROUP ? json(value) : String.valueOf(value));
        }
        return cells;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** JSON compact minimal (types JSON uniquement) : le domaine n'a pas de dépendance à une bibliothèque JSON. */
    static String json(Object value) {
        StringBuilder out = new StringBuilder();
        append(out, value);
        return out.toString();
    }

    private static void append(StringBuilder out, Object value) {
        switch (value) {
            case null -> out.append("null");
            case Boolean b -> out.append(b);
            case Number n -> out.append(n);
            case Map<?, ?> m -> {
                out.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    quote(out, String.valueOf(e.getKey()));
                    out.append(':');
                    append(out, e.getValue());
                }
                out.append('}');
            }
            case Iterable<?> items -> {
                out.append('[');
                boolean first = true;
                for (Object item : items) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    append(out, item);
                }
                out.append(']');
            }
            default -> quote(out, String.valueOf(value));
        }
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
