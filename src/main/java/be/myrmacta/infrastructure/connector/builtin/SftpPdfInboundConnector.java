package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConfigFieldType;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.InboundAttachment;
import be.myrmacta.domain.connector.model.PollingTriggerConnector;
import be.myrmacta.domain.connector.model.SftpConnectionConfig;
import be.myrmacta.domain.connector.model.SftpFileTooLargeException;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.domain.connector.port.out.SftpClientPort;
import be.myrmacta.domain.connector.port.out.SftpClientPort.RemoteFile;
import be.myrmacta.infrastructure.config.InboundConnectorProperties;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §113 (D-MAILROOM-PDF-SFTP) — connecteur TRIGGER « PDF numérisés » : interroge un dossier SFTP ; chaque PDF devient un
 * courrier, avec l'index du partenaire s'il en fournit un (JSON compagnon ou CSV par lot). Chaque PDF est remis au runtime AVANT
 * d'être déplacé : dans {@code processedDirectory} s'il est pris en charge, dans {@code rejectedDirectory} sinon. Rien n'est
 * supprimé. Le PDF suit le chemin des pièces jointes de l'e-mail (types, signature, antivirus).
 */
@Component
public class SftpPdfInboundConnector implements ConnectorDefinition, PollingTriggerConnector {

    static final String TYPE = "sftp-pdf-inbound";
    static final long MAX_INDEX_BYTES = 64 * 1024;
    static final long MAX_CSV_BYTES = 1024 * 1024;
    static final int MAX_CSV_ROWS = 500;
    private static final String PDF_PATTERN = "*.{pdf,PDF}";
    private static final String JSON_PATTERN = "*.{json,JSON}";
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final Set<String> INDEX_FORMATS = Set.of("JSON", "CSV", "NONE");
    private static final Logger log = LoggerFactory.getLogger(SftpPdfInboundConnector.class);

    private final SftpClientPort sftp;
    private final SimpleJsonPathMapper mapper;
    private final ObjectMapper objectMapper;
    private final InboundConnectorProperties inbound;

    public SftpPdfInboundConnector(SftpClientPort sftp, SimpleJsonPathMapper mapper, ObjectMapper objectMapper,
            InboundConnectorProperties inbound) {
        this.sftp = Objects.requireNonNull(sftp, "sftp");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.inbound = Objects.requireNonNull(inbound, "inbound");
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getLabel() {
        return "PDF numérisés (SFTP, interrogation)";
    }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.TRIGGER;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
                new ConfigField("sftpHost", "SFTP host", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("sftpPort", "SFTP port", ConfigFieldType.INTEGER, false, "22", List.of(), null),
                new ConfigField("sftpUsername", "SFTP username", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("sftpAuthType", "SFTP auth", ConfigFieldType.SELECT, true, "PASSWORD", List.of("PASSWORD", "PRIVATE_KEY"),
                        null),
                new ConfigField("sftpPassword", "SFTP password", ConfigFieldType.PASSWORD, false, null, List.of(), null),
                new ConfigField("sftpPrivateKey", "SFTP private key", ConfigFieldType.SECRET_TEXTAREA, false, null, List.of(),
                        "PEM-encoded SSH private key, required when sftpAuthType=PRIVATE_KEY"),
                new ConfigField("sftpHostKeyFingerprint", "SFTP host key fingerprint", ConfigFieldType.STRING, true, null, List.of(),
                        "Expected SSH host key fingerprint (OpenSSH format, e.g. SHA256:...), verified on every connection"),
                new ConfigField("remoteDirectory", "Dossier lu", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("processedDirectory", "Dossier des fichiers traités", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("rejectedDirectory", "Dossier des fichiers refusés", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("indexFormat", "Index du partenaire", ConfigFieldType.SELECT, true, "JSON", List.of("JSON", "CSV", "NONE"),
                        "JSON : un fichier compagnon de même nom par PDF ; CSV : un index par lot, une ligne par PDF ; NONE : sans index"),
                new ConfigField("csvPattern", "Fichiers d'index CSV", ConfigFieldType.STRING, false, "*.csv", List.of(), null),
                new ConfigField("csvFileColumn", "Colonne du nom de fichier (CSV)", ConfigFieldType.STRING, false, "fichier", List.of(),
                        null),
                new ConfigField("requireIndex", "Index obligatoire", ConfigFieldType.BOOLEAN, false, "true", List.of(),
                        "Vrai : un PDF sans index attend son index. Faux : il est traité aussitôt, sans métadonnées"),
                new ConfigField("pollSeconds", "Intervalle (secondes)", ConfigFieldType.INTEGER, false, "300", List.of(), null),
                new ConfigField("processKey", "Process key", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("dossierTypeCode", "Dossier type", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("mapping", "Mapping", ConfigFieldType.STRING, false, null, List.of(),
                        "Objet JSON {fields, variables} de chemins sur {\"fichier\": nom, \"index\": {…}}, ex. \"$.index.expediteur\""),
                new ConfigField("defaultFields", "Valeurs par défaut", ConfigFieldType.STRING, false, null, List.of(),
                        "Objet JSON de valeurs constantes, prises quand le mapping ne donne rien, ex. {\"canal\": \"PAPIER\"}"));
    }

    @Override
    public void validate(Map<String, Object> config) {
        if (config == null) {
            throw new ConnectorValidationException("config is required");
        }
        connectionConfig(config);
        String authType = text(config, "sftpAuthType");
        if (!"PASSWORD".equals(authType) && !"PRIVATE_KEY".equals(authType)) {
            throw new ConnectorValidationException("Unsupported sftpAuthType: " + authType);
        }
        text(config, "PASSWORD".equals(authType) ? "sftpPassword" : "sftpPrivateKey");
        String read = directory(config, "remoteDirectory");
        String processed = directory(config, "processedDirectory");
        String rejected = directory(config, "rejectedDirectory");
        if (read.equals(processed) || read.equals(rejected) || processed.equals(rejected)) {
            throw new ConnectorValidationException("processedDirectory and rejectedDirectory must differ from the directory read and from each "
                    + "other");
        }
        if (!INDEX_FORMATS.contains(indexFormat(config))) {
            throw new ConnectorValidationException("indexFormat must be JSON, CSV or NONE");
        }
        int poll = InboundMailRouting.intOr(config.get("pollSeconds"), 300);
        if (poll < inbound.minPollSeconds()) {
            throw new ConnectorValidationException("pollSeconds must be at least " + inbound.minPollSeconds());
        }
        for (String key : List.of("mapping", "defaultFields")) {
            if (config.get(key) != null && !(config.get(key) instanceof Map<?, ?>)) {
                throw new ConnectorValidationException(key + " must be a JSON object");
            }
        }
        text(config, "processKey");
        text(config, "dossierTypeCode");
    }

    @Override
    public void poll(ConnectorInstanceData instance, EventDelivery delivery) {
        Objects.requireNonNull(instance, "instance");
        Run run = new Run(instance, delivery);
        List<RemoteFile> pdfs = sorted(sftp.listFiles(run.connection, run.directory, PDF_PATTERN));
        switch (indexFormat(instance.config())) {
            case "JSON" -> pollWithCompanions(run, pdfs);
            case "CSV" -> pollWithCsvIndex(run, pdfs);
            default -> pdfs.stream().limit(inbound.maxMessagesPerPoll()).forEach(pdf -> run.take(pdf, Map.of(), null));
        }
    }

    private void pollWithCompanions(Run run, List<RemoteFile> pdfs) {
        Map<String, RemoteFile> companions = new LinkedHashMap<>();
        for (RemoteFile json : sftp.listFiles(run.connection, run.directory, JSON_PATTERN)) {
            companions.put(baseName(fileName(json.path())), json);
        }
        int taken = 0;
        for (RemoteFile pdf : pdfs) {
            if (taken >= inbound.maxMessagesPerPoll()) {
                return;
            }
            RemoteFile companion = companions.get(baseName(fileName(pdf.path())));
            if (companion == null) {
                if (!run.requireIndex) {
                    run.take(pdf, Map.of(), null);
                    taken++;
                }
                continue;
            }
            Map<String, Object> index = readCompanion(companion, run);
            if (index == null) {
                run.reject(pdf, companion, "index JSON illisible");
            } else {
                run.take(pdf, index, companion);
            }
            taken++;
        }
    }

    private void pollWithCsvIndex(Run run, List<RemoteFile> pdfs) {
        Map<String, RemoteFile> byName = new LinkedHashMap<>();
        pdfs.forEach(pdf -> byName.put(fileName(pdf.path()), pdf));
        Set<String> referenced = new HashSet<>();
        String column = InboundMailRouting.stringOr(run.config.get("csvFileColumn"), "fichier");
        String pattern = InboundMailRouting.stringOr(run.config.get("csvPattern"), "*.csv");
        int taken = 0;
        int cap = inbound.maxMessagesPerPoll();
        for (RemoteFile csv : sorted(sftp.listFiles(run.connection, run.directory, pattern))) {
            if (taken >= cap) {
                return;
            }
            List<Map<String, Object>> rows = readCsv(csv, run);
            if (rows == null) {
                sftp.archive(run.connection, csv.path(), run.rejected);
                continue;
            }
            boolean finished = true;
            for (Map<String, Object> row : rows) {
                String name = String.valueOf(row.getOrDefault(column, "")).strip();
                RemoteFile pdf = byName.get(name);
                if (pdf == null || name.contains("/") || referenced.contains(name)) {
                    // Absent : pas encore déposé, ou déjà traité lors d'un passage précédent (index entamé).
                    log.debug("{}: instance {} index {} names a missing or repeated file, line skipped", TYPE, run.instance.id(),
                            fileName(csv.path()));
                    continue;
                }
                if (taken >= cap) {
                    // Revue de clôture : le plafond par interrogation vaut aussi pour un index ; l'index reste en place et ses
                    // lignes restantes sont reprises au passage suivant.
                    finished = false;
                    break;
                }
                referenced.add(name);
                run.take(pdf, row, null);
                taken++;
            }
            if (finished) {
                sftp.archive(run.connection, csv.path(), run.processed);
            }
        }
        if (!run.requireIndex && taken < cap) {
            pdfs.stream().filter(pdf -> !referenced.contains(fileName(pdf.path()))).limit(cap - taken)
                    .forEach(pdf -> run.take(pdf, Map.of(), null));
        }
    }

    private Map<String, Object> readCompanion(RemoteFile companion, Run run) {
        if (companion.sizeBytes() > MAX_INDEX_BYTES) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(sftp.download(run.connection, companion.path(), MAX_INDEX_BYTES));
            if (node == null || !node.isObject()) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> index = objectMapper.convertValue(node, Map.class);
            return index;
        } catch (SftpFileTooLargeException | tools.jackson.core.JacksonException unreadable) {
            return null;
        }
    }

    private List<Map<String, Object>> readCsv(RemoteFile csv, Run run) {
        if (csv.sizeBytes() > MAX_CSV_BYTES) {
            log.warn("{}: instance {} index {} too large, moved to the rejected directory", TYPE, run.instance.id(), fileName(csv.path()));
            return null;
        }
        try (var reader = new InputStreamReader(new ByteArrayInputStream(sftp.download(run.connection, csv.path(), MAX_CSV_BYTES)),
                StandardCharsets.UTF_8)) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (CSVRecord record : CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).get().parse(reader)) {
                if (rows.size() >= MAX_CSV_ROWS) {
                    log.warn("{}: instance {} index {} has more than {} lines, moved to the rejected directory", TYPE, run.instance.id(),
                            fileName(csv.path()), MAX_CSV_ROWS);
                    return null;
                }
                rows.add(new LinkedHashMap<>(record.toMap()));
            }
            return rows;
        } catch (IOException | IllegalArgumentException | IllegalStateException | SftpFileTooLargeException unreadable) {
            log.warn("{}: instance {} index {} unreadable, moved to the rejected directory", TYPE, run.instance.id(), fileName(csv.path()));
            return null;
        }
    }

    /** Une interrogation d'une instance : configuration lue une fois, remise et rangement de chaque PDF. */
    private final class Run {

        private final ConnectorInstanceData instance;
        private final EventDelivery delivery;
        private final Map<String, Object> config;
        private final SftpConnectionConfig connection;
        private final String directory;
        private final String processed;
        private final String rejected;
        private final boolean requireIndex;
        private final Map<String, Object> fieldPaths;
        private final Map<String, Object> variablePaths;
        private final Map<String, Object> defaults;

        private Run(ConnectorInstanceData instance, EventDelivery delivery) {
            this.instance = instance;
            this.delivery = Objects.requireNonNull(delivery, "delivery");
            this.config = instance.config();
            this.connection = connectionConfig(config);
            this.directory = directory(config, "remoteDirectory");
            this.processed = directory(config, "processedDirectory");
            this.rejected = directory(config, "rejectedDirectory");
            this.requireIndex = !"false".equals(String.valueOf(config.get("requireIndex")));
            Map<String, Object> mapping = InboundMailRouting.asMap(config.get("mapping"));
            this.fieldPaths = InboundMailRouting.asMap(mapping.get("fields"));
            this.variablePaths = InboundMailRouting.asMap(mapping.get("variables"));
            this.defaults = InboundMailRouting.asMap(config.get("defaultFields"));
        }

        /** Le PDF est remis au runtime, puis rangé dans « traités » ; tout refus ou échec de remise le range dans « refusés ». */
        private void take(RemoteFile pdf, Map<String, Object> index, RemoteFile companion) {
            String name = fileName(pdf.path());
            if (pdf.sizeBytes() > inbound.maxAttachmentBytes()) {
                reject(pdf, companion, "trop volumineux");
                return;
            }
            byte[] content;
            try {
                content = sftp.download(connection, pdf.path(), inbound.maxAttachmentBytes());
            } catch (SftpFileTooLargeException tooLarge) {
                reject(pdf, companion, "trop volumineux");
                return;
            }
            if (!startsWith(content, PDF_MAGIC)) {
                reject(pdf, companion, "pas un PDF");
                return;
            }
            Map<String, Object> payload = Map.of("fichier", name, "index", index);
            Map<String, Object> fields = new LinkedHashMap<>(defaults);
            for (Map.Entry<String, Object> path : fieldPaths.entrySet()) {
                Object value = mapper.read(payload, String.valueOf(path.getValue()));
                if (value != null && !(value instanceof String text && text.isBlank())) {
                    fields.put(path.getKey(), value);
                } else if (!defaults.containsKey(path.getKey())) {
                    reject(pdf, companion, "champ mappé vide : " + path.getKey());
                    return;
                }
            }
            Map<String, Object> variables = new LinkedHashMap<>();
            for (Map.Entry<String, Object> path : variablePaths.entrySet()) {
                Object value = mapper.read(payload, String.valueOf(path.getValue()));
                if (value != null) {
                    variables.put(path.getKey(), value);
                }
            }
            variables.keySet().removeAll(InboundMailRouting.PLATFORM_VARIABLES);
            variables.put("courrierFichier", name);
            try {
                delivery.deliver(new WorkflowTriggerEvent(text(config, "processKey"), text(config, "dossierTypeCode"), fields, variables,
                        null, List.of(new InboundAttachment(name, "application/pdf", content)), true));
            } catch (RuntimeException failure) {
                reject(pdf, companion, "prise en charge impossible (" + failure.getClass().getSimpleName() + ")");
                return;
            }
            move(pdf, companion, processed);
        }

        private void reject(RemoteFile pdf, RemoteFile companion, String reason) {
            log.warn("{}: instance {} file {} refused ({}): moved to the rejected directory", TYPE, instance.id(), fileName(pdf.path()),
                    reason);
            move(pdf, companion, rejected);
        }

        private void move(RemoteFile pdf, RemoteFile companion, String target) {
            sftp.archive(connection, pdf.path(), target);
            if (companion != null) {
                sftp.archive(connection, companion.path(), target);
            }
        }
    }

    private static List<RemoteFile> sorted(List<RemoteFile> files) {
        List<RemoteFile> copy = new ArrayList<>(files);
        copy.sort(Comparator.comparing(RemoteFile::path));
        return copy;
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String baseName(String name) {
        int dot = name.lastIndexOf('.');
        return (dot < 0 ? name : name.substring(0, dot)).toLowerCase(Locale.ROOT);
    }

    private static String indexFormat(Map<String, Object> config) {
        return InboundMailRouting.stringOr(config.get("indexFormat"), "JSON").strip().toUpperCase(Locale.ROOT);
    }

    private static String directory(Map<String, Object> config, String key) {
        String value = text(config, key);
        if (!value.matches("[^\\p{Cntrl}]{1,255}")) {
            throw new ConnectorValidationException(key + " is not a valid directory");
        }
        return value.endsWith("/") && value.length() > 1 ? value.substring(0, value.length() - 1) : value;
    }

    private static String text(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ConnectorValidationException(key + " is required");
        }
        return String.valueOf(value).strip();
    }

    private static SftpConnectionConfig connectionConfig(Map<String, Object> config) {
        int port = InboundMailRouting.intOr(config.get("sftpPort"), 22);
        return new SftpConnectionConfig(text(config, "sftpHost"), port, text(config, "sftpUsername"),
                InboundMailRouting.stringOr(config.get("sftpPassword"), null), InboundMailRouting.stringOr(config.get("sftpPrivateKey"), null),
                text(config, "sftpAuthType"), text(config, "sftpHostKeyFingerprint"));
    }
}
