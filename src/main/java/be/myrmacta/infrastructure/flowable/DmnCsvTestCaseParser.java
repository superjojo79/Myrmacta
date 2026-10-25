package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.workflow.model.DecisionTestCase;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/** RFC 4180 CSV: columns {@code input.<name>} and {@code expected.<name>}, one expected row per line. */
@Component
public class DmnCsvTestCaseParser {

    static final int MAX_CASES = 100;

    public List<DecisionTestCase> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            throw new IllegalArgumentException("CSV is empty");
        }
        CSVFormat format = CSVFormat.RFC4180.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .get();
        try (CSVParser parser = CSVParser.parse(new StringReader(csv), format)) {
            List<String> headers = parser.getHeaderNames();
            validateHeaders(headers);
            List<DecisionTestCase> cases = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (cases.size() >= MAX_CASES) {
                    throw new IllegalArgumentException("at most " + MAX_CASES + " cases are allowed");
                }
                cases.add(toCase(headers, record));
            }
            if (cases.isEmpty()) {
                throw new IllegalArgumentException("CSV has no test cases");
            }
            return List.copyOf(cases);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new IllegalArgumentException("CSV is malformed: " + ex.getMessage(), ex);
        }
    }

    private static void validateHeaders(List<String> headers) {
        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("CSV header is required");
        }
        boolean input = false;
        boolean expected = false;
        for (String header : headers) {
            if (header.startsWith("input.") && header.length() > "input.".length()) {
                input = true;
                continue;
            }
            if (header.startsWith("expected.") && header.length() > "expected.".length()) {
                expected = true;
                continue;
            }
            throw new IllegalArgumentException("CSV column is not allowed: " + header);
        }
        if (!input || !expected) {
            throw new IllegalArgumentException("CSV must declare input.* and expected.* columns");
        }
    }

    private static DecisionTestCase toCase(List<String> headers, CSVRecord record) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        Map<String, Object> expected = new LinkedHashMap<>();
        for (String header : headers) {
            String cell = record.isMapped(header) ? record.get(header) : "";
            Object value = coerce(cell);
            if (header.startsWith("input.")) {
                inputs.put(header.substring("input.".length()), value);
            } else {
                expected.put(header.substring("expected.".length()), value);
            }
        }
        return new DecisionTestCase(copy(inputs), List.of(copy(expected)));
    }

    /** {@link Map#copyOf} rejects null cells; a blank CSV cell is a null value. */
    private static Map<String, Object> copy(Map<String, Object> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    static Object coerce(String cell) {
        if (cell == null || cell.isBlank()) {
            return null;
        }
        String trimmed = cell.trim();
        if ("true".equalsIgnoreCase(trimmed)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return Boolean.FALSE;
        }
        if (trimmed.matches("-?\\d+")) {
            try {
                return Integer.valueOf(trimmed);
            } catch (NumberFormatException ex) {
                return new BigDecimal(trimmed);
            }
        }
        if (trimmed.matches("-?\\d+\\.\\d+")) {
            return new BigDecimal(trimmed);
        }
        return trimmed;
    }
}
