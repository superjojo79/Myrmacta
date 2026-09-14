package com.flowforge.infrastructure.connector.builtin;

import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConfigFieldType;
import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.model.SftpConnectionConfig;
import com.flowforge.domain.connector.model.TriggerConnector;
import com.flowforge.domain.connector.model.WorkflowTriggerEvent;
import com.flowforge.domain.connector.port.out.SftpClientPort;
import com.flowforge.infrastructure.config.ConnectorProperties;
import com.flowforge.infrastructure.connector.mapping.SimpleJsonPathMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * TRIGGER connector (connecteurs-cible, lot B1) : ingère un ou plusieurs fichiers CSV/Excel
 * depuis un serveur SFTP à chaque déclenchement cron ({@link BatchImportConnectorTriggerJob}
 * décide du <em>quand</em>, comme {@code SchedulerConnector}/{@code SchedulerConnectorTriggerJob}
 * — cette classe ignore {@code rawPayload}). Un dossier par ligne (D-BATCH-GRANULARITY) ; une
 * ligne invalide est isolée ici, jamais propagée à {@code ConnectorTriggerRuntimeImpl}
 * (D-BATCH-ERROR-HANDLING) — contrairement à {@code HttpRestConnector.onEvent}, dont un item
 * invalide interrompt tout le lot.
 */
@Component
public class BatchImportConnector implements ConnectorDefinition, TriggerConnector {

    private static final Logger log = LoggerFactory.getLogger(BatchImportConnector.class);

    private final SftpClientPort sftpClient;
    private final SimpleJsonPathMapper mapper;
    private final ConnectorProperties properties;

    public BatchImportConnector(SftpClientPort sftpClient, SimpleJsonPathMapper mapper, ConnectorProperties properties) {
        this.sftpClient = Objects.requireNonNull(sftpClient, "sftpClient");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public String getType() {
        return "batch-import";
    }

    @Override
    public String getLabel() {
        return "Batch import (SFTP, CSV/Excel, cron trigger)";
    }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.TRIGGER;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
                new ConfigField("sftpHost", "SFTP host", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField(
                        "sftpPort", "SFTP port", ConfigFieldType.INTEGER, false, "22", List.of(), null),
                new ConfigField("sftpUsername", "SFTP username", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField(
                        "sftpAuthType",
                        "SFTP auth",
                        ConfigFieldType.SELECT,
                        true,
                        "PASSWORD",
                        List.of("PASSWORD", "PRIVATE_KEY"),
                        null),
                new ConfigField(
                        "sftpPassword", "SFTP password", ConfigFieldType.PASSWORD, false, null, List.of(), null),
                new ConfigField(
                        "sftpPrivateKey",
                        "SFTP private key",
                        ConfigFieldType.SECRET_TEXTAREA,
                        false,
                        null,
                        List.of(),
                        "PEM-encoded SSH private key, required when sftpAuthType=PRIVATE_KEY"),
                new ConfigField(
                        "sftpHostKeyFingerprint",
                        "SFTP host key fingerprint",
                        ConfigFieldType.STRING,
                        true,
                        null,
                        List.of(),
                        "Expected SSH host key fingerprint (OpenSSH format, e.g. SHA256:...), "
                                + "verified on every connection — never accepted blindly"),
                new ConfigField(
                        "remoteDirectory", "Remote directory", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField(
                        "filenamePattern",
                        "Filename pattern",
                        ConfigFieldType.STRING,
                        true,
                        null,
                        List.of(),
                        "Glob pattern, e.g. *.csv or export-*.xlsx"),
                new ConfigField(
                        "archiveDirectory",
                        "Archive directory",
                        ConfigFieldType.STRING,
                        true,
                        null,
                        List.of(),
                        "Processed files are moved here to avoid re-import on the next poll"),
                new ConfigField(
                        "cronExpression", "Cron expression", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("processKey", "Process key", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField(
                        "dossierTypeCode", "Dossier type", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField(
                        "mapping",
                        "Row mapping",
                        ConfigFieldType.STRING,
                        false,
                        null,
                        List.of(),
                        "JSON object of column-name JSONPath mappings (fields, variables, processKeyPath, …)"));
    }

    @Override
    public void validate(Map<String, Object> config) {
        if (config == null) {
            throw new ConnectorValidationException("config is required");
        }
        requireString(config, "sftpHost");
        requireString(config, "sftpUsername");
        String authType = requireString(config, "sftpAuthType");
        if (!"PASSWORD".equals(authType) && !"PRIVATE_KEY".equals(authType)) {
            throw new ConnectorValidationException("Unsupported sftpAuthType: " + authType);
        }
        if ("PASSWORD".equals(authType)) {
            requireString(config, "sftpPassword");
        } else {
            requireString(config, "sftpPrivateKey");
        }
        requireString(config, "sftpHostKeyFingerprint");
        requireString(config, "remoteDirectory");
        requireString(config, "filenamePattern");
        requireString(config, "archiveDirectory");
        String cronExpression = requireString(config, "cronExpression");
        try {
            CronExpression.parse(cronExpression);
        } catch (IllegalArgumentException ex) {
            throw new ConnectorValidationException("Invalid cronExpression: " + ex.getMessage());
        }
        requireString(config, "processKey");
        requireString(config, "dossierTypeCode");
    }

    @Override
    public List<WorkflowTriggerEvent> onEvent(ConnectorInstanceData instance, Object rawPayload) {
        Objects.requireNonNull(instance, "instance");
        Map<String, Object> config = instance.config();
        SftpConnectionConfig connectionConfig = connectionConfig(config);
        String remoteDirectory = requireString(config, "remoteDirectory");
        String filenamePattern = requireString(config, "filenamePattern");
        String archiveDirectory = requireString(config, "archiveDirectory");

        List<SftpClientPort.RemoteFile> files = new ArrayList<>(
                sftpClient.listFiles(connectionConfig, remoteDirectory, filenamePattern));
        files.sort(Comparator.comparing(SftpClientPort.RemoteFile::path));

        List<WorkflowTriggerEvent> events = new ArrayList<>();
        long maxFileBytes = properties.batchImport().maxFileBytes();
        int maxRows = properties.batchImport().maxRows();

        for (SftpClientPort.RemoteFile file : files) {
            if (file.sizeBytes() > maxFileBytes) {
                log.warn(
                        "batch-import: file {} ({} bytes) exceeds maxFileBytes ({}), rejected without download",
                        file.path(), file.sizeBytes(), maxFileBytes);
                continue;
            }
            byte[] content = sftpClient.download(connectionConfig, file.path());
            List<Map<String, Object>> rows = parseRows(file.path(), content);
            if (rows.size() > maxRows) {
                log.warn(
                        "batch-import: file {} ({} rows) exceeds maxRows ({}), rejected entirely",
                        file.path(), rows.size(), maxRows);
                continue;
            }
            events.addAll(mapRows(instance, config, rows, file.path()));
            sftpClient.archive(connectionConfig, file.path(), archiveDirectory);
        }
        return List.copyOf(events);
    }

    private List<WorkflowTriggerEvent> mapRows(
            ConnectorInstanceData instance, Map<String, Object> config, List<Map<String, Object>> rows, String filePath) {
        String defaultProcessKey = stringOr(config.get("processKey"), null);
        String defaultType = stringOr(config.get("dossierTypeCode"), null);
        Map<String, Object> mapping = asMap(config.get("mapping"));
        Map<String, Object> fieldPaths = asMap(mapping.get("fields"));
        Map<String, Object> variablePaths = asMap(mapping.get("variables"));
        String processKeyPath = stringOr(mapping.get("processKeyPath"), null);
        String typePath = stringOr(mapping.get("dossierTypeCodePath"), null);
        String existingRefPath = stringOr(mapping.get("existingDossierReferencePath"), null);

        List<WorkflowTriggerEvent> events = new ArrayList<>();
        int skipped = 0;
        for (Map<String, Object> row : rows) {
            try {
                Map<String, Object> fields = mapPaths(row, fieldPaths);
                if (hasBlankMappedField(fields)) {
                    throw new ConnectorValidationException("mapped field missing/blank in row");
                }
                Map<String, Object> variables = mapPaths(row, variablePaths);
                String processKey = processKeyPath == null
                        ? defaultProcessKey
                        : stringOr(mapper.read(row, processKeyPath), defaultProcessKey);
                String dossierType = typePath == null
                        ? defaultType
                        : stringOr(mapper.read(row, typePath), defaultType);
                String existingRef = existingRefPath == null ? null : stringOr(mapper.read(row, existingRefPath), null);
                if (processKey == null || processKey.isBlank()) {
                    throw new ConnectorValidationException("processKey is required for trigger event");
                }
                events.add(new WorkflowTriggerEvent(processKey, dossierType, fields, variables, existingRef));
            } catch (ConnectorValidationException invalidRow) {
                skipped++;
                log.warn("batch-import: skipping invalid row in {}: {}", filePath, invalidRow.getMessage());
            }
        }
        if (skipped > 0) {
            log.warn("batch-import: {} invalid row(s) skipped in {}", skipped, filePath);
        }
        return events;
    }

    /**
     * A mapped field ({@code mapping.fields}) resolving to {@code null}/blank means the source
     * column was empty for this row — treated as "champ mappé requis manquant" (D-BATCH-ERROR-
     * HANDLING, requirements): the row is excluded, not the whole file.
     */
    private static boolean hasBlankMappedField(Map<String, Object> fields) {
        for (Object value : fields.values()) {
            if (value == null || (value instanceof String text && text.isBlank())) {
                return true;
            }
        }
        return false;
    }

    private List<Map<String, Object>> parseRows(String path, byte[] content) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return parseCsv(content);
        }
        if (lower.endsWith(".xlsx")) {
            return parseXlsx(content);
        }
        throw new ConnectorValidationException("Unsupported file extension: " + path);
    }

    private static List<Map<String, Object>> parseCsv(byte[] content) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (var reader = new java.io.InputStreamReader(
                new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .get()
                    .parse(reader);
            for (CSVRecord record : records) {
                Map<String, Object> row = new LinkedHashMap<>();
                record.toMap().forEach(row::put);
                rows.add(row);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return rows;
    }

    private static List<Map<String, Object>> parseXlsx(byte[] content) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return rows;
            }
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(cell.getStringCellValue());
            }
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row dataRow = sheet.getRow(r);
                if (dataRow == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = dataRow.getCell(c);
                    row.put(headers.get(c), cell == null ? null : cellValue(cell));
                }
                rows.add(row);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return rows;
    }

    private static Object cellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case BOOLEAN -> cell.getBooleanCellValue();
            case FORMULA -> cell.getCellFormula();
            default -> cell.getStringCellValue();
        };
    }

    private static SftpConnectionConfig connectionConfig(Map<String, Object> config) {
        int port = config.get("sftpPort") instanceof Number n ? n.intValue() : 22;
        return new SftpConnectionConfig(
                requireString(config, "sftpHost"),
                port,
                requireString(config, "sftpUsername"),
                stringOr(config.get("sftpPassword"), null),
                stringOr(config.get("sftpPrivateKey"), null),
                requireString(config, "sftpHostKeyFingerprint"));
    }

    private Map<String, Object> mapPaths(Object item, Map<String, Object> paths) {
        if (paths == null || paths.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : paths.entrySet()) {
            out.put(entry.getKey(), mapper.read(item, String.valueOf(entry.getValue())));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String stringOr(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    private static String requireString(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ConnectorValidationException(key + " is required");
        }
        return text;
    }
}
