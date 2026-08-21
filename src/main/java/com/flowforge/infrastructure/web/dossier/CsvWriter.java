package com.flowforge.infrastructure.web.dossier;

import java.util.List;

/** Minimal RFC4180 field escaping — no external CSV dependency (stack conventions). */
final class CsvWriter {

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
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (!needsQuoting) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
