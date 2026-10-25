package be.myrmacta.infrastructure.web.dossier;

import java.util.List;

final class CsvWriter {

    private CsvWriter() {}

    static String writeRow(List<String> fields) {
        return be.myrmacta.infrastructure.web.csv.CsvWriter.writeRow(fields);
    }
}
