package com.flowforge.infrastructure.web.dossier;

import java.util.List;

final class CsvWriter {

    private CsvWriter() {}

    static String writeRow(List<String> fields) {
        return com.flowforge.infrastructure.web.csv.CsvWriter.writeRow(fields);
    }
}
