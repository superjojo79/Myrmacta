package be.myrmacta.infrastructure.web.csv;

import java.util.List;

/**
 * RFC4180 escaping + OWASP CSV/formula injection neutralization.
 */
public final class CsvWriter {

    private static final String FORMULA_TRIGGER_CHARS = "=+-@\t";

    private CsvWriter() {}

    public static String writeRow(List<String> fields) {
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
