package com.flowforge.infrastructure.web.dossier;

import java.util.List;

/**
 * Minimal RFC4180 field escaping — no external CSV dependency (stack conventions). Also
 * neutralizes leading {@code = + - @} and tab (OWASP CSV/formula injection): dossier labels and
 * dynamic field values are user-controlled, and a spreadsheet app opening this export would
 * otherwise evaluate a value like {@code =cmd|'/c calc'!A0} as a formula.
 */
final class CsvWriter {

    private static final String FORMULA_TRIGGER_CHARS = "=+-@\t";

    private CsvWriter() {}

    static String writeRow(List<String> fields) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(escape(fields.get(i)));
        }
        row.append("\r\n");
        return row.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        String neutralized = neutralizeFormulaTrigger(value);
        boolean needsQuoting = neutralized.contains(",")
                || neutralized.contains("\"")
                || neutralized.contains("\n")
                || neutralized.contains("\r");
        if (!needsQuoting) {
            return neutralized;
        }
        return "\"" + neutralized.replace("\"", "\"\"") + "\"";
    }

    private static String neutralizeFormulaTrigger(String value) {
        if (!value.isEmpty() && FORMULA_TRIGGER_CHARS.indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }
}
