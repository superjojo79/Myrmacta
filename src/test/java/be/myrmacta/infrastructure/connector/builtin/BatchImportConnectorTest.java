package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.SftpConnectionConfig;
import be.myrmacta.domain.connector.model.SftpConnectionException;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.domain.connector.port.out.SftpClientPort;
import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BatchImportConnectorTest {

    private static final ConnectorProperties DEFAULT_PROPERTIES =
            new ConnectorProperties(null, null, null, null, null, null);

    private FakeSftpClientPort sftp;
    private BatchImportConnector connector;

    @BeforeEach
    void setUp() {
        sftp = new FakeSftpClientPort();
        connector = new BatchImportConnector(sftp, new SimpleJsonPathMapper(), DEFAULT_PROPERTIES);
    }

    @Test
    void capability_isTriggerOnly() {
        assertThat(connector.getCapability()).isEqualTo(ConnectorCapability.TRIGGER);
    }

    // --- validate() ---

    @Test
    void validate_missingRequiredFields_throws() {
        assertThatThrownBy(() -> connector.validate(Map.of()))
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Test
    void validate_passwordAuthWithoutPassword_throws() {
        Map<String, Object> config = baseConfig();
        config.put("sftpAuthType", "PASSWORD");
        config.remove("sftpPassword");

        assertThatThrownBy(() -> connector.validate(config)).isInstanceOf(ConnectorValidationException.class);
    }

    @Test
    void validate_privateKeyAuthWithoutKey_throws() {
        Map<String, Object> config = baseConfig();
        config.put("sftpAuthType", "PRIVATE_KEY");
        config.remove("sftpPassword");

        assertThatThrownBy(() -> connector.validate(config)).isInstanceOf(ConnectorValidationException.class);
    }

    @Test
    void validate_privateKeyAuthWithKey_passes() {
        Map<String, Object> config = baseConfig();
        config.put("sftpAuthType", "PRIVATE_KEY");
        config.remove("sftpPassword");
        config.put("sftpPrivateKey", "-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n-----END OPENSSH PRIVATE KEY-----");

        connector.validate(config);
    }

    @Test
    void validate_missingHostKeyFingerprint_throws() {
        Map<String, Object> config = baseConfig();
        config.remove("sftpHostKeyFingerprint");

        assertThatThrownBy(() -> connector.validate(config)).isInstanceOf(ConnectorValidationException.class);
    }

    @Test
    void validate_defaultPort_whenAbsent() {
        Map<String, Object> config = baseConfig();
        config.remove("sftpPort");
        registerCsv("/in", "/in/a.csv", "name\nAlice\n");

        connector.onEvent(instance(config), null);

        assertThat(sftp.lastConnectionConfig.port()).isEqualTo(22);
    }

    @Test
    void connectionConfig_carriesSftpAuthTypeExplicitly_evenWithLeftoverPrivateKeyField() {
        // Revue adversariale (connecteurs-cible, lot B1) : la méthode d'authentification réelle
        // doit venir de sftpAuthType, pas être devinée depuis la présence d'un privateKeyPem —
        // sinon une clé privée laissée en base après un retour à PASSWORD resterait active.
        Map<String, Object> config = baseConfig();
        config.put("sftpAuthType", "PASSWORD");
        config.put("sftpPrivateKey", "-----BEGIN OPENSSH PRIVATE KEY-----\nleftover\n-----END OPENSSH PRIVATE KEY-----");
        registerCsv("/in", "/in/a.csv", "name\nAlice\n");

        connector.onEvent(instance(config), null);

        assertThat(sftp.lastConnectionConfig.authType()).isEqualTo("PASSWORD");
    }

    // --- onEvent(): CSV ---

    @Test
    void onEvent_csv_mapsOneEventPerRow() {
        Map<String, Object> config = baseConfig();
        config.put("mapping", Map.of("fields", Map.of("clientName", "$.name")));
        registerCsv("/in", "/in/a.csv", "name\nAlice\nBob\n");

        List<WorkflowTriggerEvent> events = connector.onEvent(instance(config), null);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).dossierFields()).containsEntry("clientName", "Alice");
        assertThat(events.get(1).dossierFields()).containsEntry("clientName", "Bob");
        assertThat(events).allSatisfy(e -> assertThat(e.processKey()).isEqualTo("importProcess"));
    }

    @Test
    void onEvent_csv_emptyFile_returnsNoEvents() {
        Map<String, Object> config = baseConfig();
        registerCsv("/in", "/in/a.csv", "name\n");

        List<WorkflowTriggerEvent> events = connector.onEvent(instance(config), null);

        assertThat(events).isEmpty();
    }

    // --- onEvent(): XLSX ---

    @Test
    void onEvent_xlsx_mapsOneEventPerRow() {
        Map<String, Object> config = baseConfig();
        config.put("filenamePattern", "*.xlsx");
        config.put("mapping", Map.of("fields", Map.of("clientName", "$.name")));
        registerXlsx("/in", "/in/a.xlsx", "name", "Alice", "Bob");

        List<WorkflowTriggerEvent> events = connector.onEvent(instance(config), null);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).dossierFields()).containsEntry("clientName", "Alice");
        assertThat(events.get(1).dossierFields()).containsEntry("clientName", "Bob");
    }

    // --- Isolation ligne invalide (D-BATCH-ERROR-HANDLING) ---

    @Test
    void onEvent_rowWithBlankMappedField_isSkipped_othersStillProduceEvents() {
        Map<String, Object> config = baseConfig();
        config.put("mapping", Map.of("fields", Map.of("clientName", "$.name")));
        registerCsv("/in", "/in/a.csv", "name,other\n,x\nBob,y\n");

        List<WorkflowTriggerEvent> events = connector.onEvent(instance(config), null);

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().dossierFields()).containsEntry("clientName", "Bob");
    }

    @Test
    void onEvent_allRowsInvalid_returnsEmptyList_noException() {
        Map<String, Object> config = baseConfig();
        config.put("mapping", Map.of("fields", Map.of("clientName", "$.name")));
        registerCsv("/in", "/in/a.csv", "name,other\n,x\n,y\n");

        List<WorkflowTriggerEvent> events = connector.onEvent(instance(config), null);

        assertThat(events).isEmpty();
        assertThat(sftp.archived).containsExactly("/in/a.csv");
    }

    // --- Garde taille / lignes (D-BATCH-SIZE-LIMIT) ---

    @Test
    void onEvent_fileExceedingMaxFileBytes_isRejectedWithoutDownloadOrArchive() {
        ConnectorProperties smallLimit =
                new ConnectorProperties(null, null, null, null, new ConnectorProperties.BatchImport(4L, 1_000), null);
        connector = new BatchImportConnector(sftp, new SimpleJsonPathMapper(), smallLimit);
        Map<String, Object> config = baseConfig();
        sftp.filesByDirectory.put("/in", List.of(new SftpClientPort.RemoteFile("/in/big.csv", 5_000L)));

        List<WorkflowTriggerEvent> events = connector.onEvent(instance(config), null);

        assertThat(events).isEmpty();
        assertThat(sftp.downloaded).isEmpty();
        assertThat(sftp.archived).isEmpty();
    }

    @Test
    void onEvent_serverMisreportsSmallerSizeThanActualContent_rejectedWithoutOom_otherFilesStillProcessed() {
        // Revue adversariale (connecteurs-cible, lot B1) : le pré-contrôle ne fait confiance qu'à
        // la taille annoncée par listFiles() ; un serveur SFTP menteur/défaillant pourrait annoncer
        // une petite taille puis servir davantage d'octets au download() réel — la garde doit donc
        // aussi s'appliquer pendant le téléchargement, pas seulement avant.
        ConnectorProperties smallLimit = new ConnectorProperties(
                null, null, null, null, new ConnectorProperties.BatchImport(20L, 1_000), null);
        connector = new BatchImportConnector(sftp, new SimpleJsonPathMapper(), smallLimit);
        Map<String, Object> config = baseConfig();
        byte[] lyingActualContent = "name\nAlice\nBob\nCarol\n".getBytes(StandardCharsets.UTF_8); // 21 octets réels
        byte[] okContent = "name\nBob\n".getBytes(StandardCharsets.UTF_8); // 9 octets, sous la limite
        sftp.filesByDirectory.put(
                "/in",
                List.of(
                        // taille annoncée (5) très inférieure à la limite (20) ET au contenu réel (21)
                        new SftpClientPort.RemoteFile("/in/lying.csv", 5L),
                        new SftpClientPort.RemoteFile("/in/ok.csv", okContent.length)));
        sftp.contentsByPath.put("/in/lying.csv", lyingActualContent);
        sftp.contentsByPath.put("/in/ok.csv", okContent);

        List<WorkflowTriggerEvent> events = connector.onEvent(instance(config), null);

        assertThat(sftp.downloaded).contains("/in/lying.csv"); // le pré-contrôle laisse passer, à tort
        assertThat(sftp.archived).containsExactly("/in/ok.csv"); // seul le fichier honnête est archivé
        // Le fichier menteur ne plante pas le traitement des autres fichiers du même poll (1 ligne "Bob").
        assertThat(events).hasSize(1);
    }

    @Test
    void onEvent_fileExceedingMaxRows_isRejectedEntirely_noArchive() {
        ConnectorProperties smallLimit = new ConnectorProperties(
                null, null, null, null, new ConnectorProperties.BatchImport(1_000_000L, 2), null);
        connector = new BatchImportConnector(sftp, new SimpleJsonPathMapper(), smallLimit);
        Map<String, Object> config = baseConfig();
        registerCsv("/in", "/in/a.csv", "name\nAlice\nBob\nCarol\n");

        List<WorkflowTriggerEvent> events = connector.onEvent(instance(config), null);

        assertThat(events).isEmpty();
        assertThat(sftp.archived).isEmpty();
    }

    // --- Listing / multi-fichiers (D-BATCH-PATH) ---

    @Test
    void onEvent_noFileMatches_returnsEmptyList_noDownloadAttempted() {
        Map<String, Object> config = baseConfig();
        sftp.filesByDirectory.put("/in", List.of());

        List<WorkflowTriggerEvent> events = connector.onEvent(instance(config), null);

        assertThat(events).isEmpty();
        assertThat(sftp.downloaded).isEmpty();
    }

    @Test
    void onEvent_multipleFiles_bothProcessedAndArchived() {
        Map<String, Object> config = baseConfig();
        registerCsv("/in", "/in/a.csv", "name\nAlice\n");
        registerCsv("/in", "/in/b.csv", "name\nBob\n");

        List<WorkflowTriggerEvent> events = connector.onEvent(instance(config), null);

        assertThat(events).hasSize(2);
        assertThat(sftp.archived).containsExactlyInAnyOrder("/in/a.csv", "/in/b.csv");
    }

    // --- Archivage (D-BATCH-DEDUP) ---

    @Test
    void onEvent_processedFile_isArchived() {
        Map<String, Object> config = baseConfig();
        registerCsv("/in", "/in/a.csv", "name\nAlice\n");

        connector.onEvent(instance(config), null);

        assertThat(sftp.archived).containsExactly("/in/a.csv");
        assertThat(sftp.lastArchiveDirectory).isEqualTo("/archive");
    }

    // --- Échec connexion SFTP (D-BI-SFTP-CONN-FAILURE) ---

    @Test
    void onEvent_sftpConnectionFailure_propagates() {
        Map<String, Object> config = baseConfig();
        sftp.failListingWith(new SftpConnectionException("connection refused", null));

        assertThatThrownBy(() -> connector.onEvent(instance(config), null))
                .isInstanceOf(SftpConnectionException.class);
    }

    // --- helpers ---

    private static Map<String, Object> baseConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("sftpHost", "sftp.example.com");
        config.put("sftpPort", 22);
        config.put("sftpUsername", "myrmacta");
        config.put("sftpAuthType", "PASSWORD");
        config.put("sftpPassword", "secret12345678901234");
        config.put("sftpHostKeyFingerprint", "SHA256:abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG");
        config.put("remoteDirectory", "/in");
        config.put("filenamePattern", "*.csv");
        config.put("archiveDirectory", "/archive");
        config.put("cronExpression", "0 0 * * * *");
        config.put("processKey", "importProcess");
        config.put("dossierTypeCode", "IMPORT");
        return config;
    }

    private static ConnectorInstanceData instance(Map<String, Object> config) {
        return new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "batch", "batch-import", true, new LinkedHashMap<>(config));
    }

    private void registerCsv(String directory, String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        sftp.filesByDirectory
                .computeIfAbsent(directory, d -> new ArrayList<>())
                .add(new SftpClientPort.RemoteFile(path, bytes.length));
        sftp.contentsByPath.put(path, bytes);
    }

    private void registerXlsx(String directory, String path, String header, String... dataRows) {
        byte[] bytes = buildXlsx(header, dataRows);
        sftp.filesByDirectory
                .computeIfAbsent(directory, d -> new ArrayList<>())
                .add(new SftpClientPort.RemoteFile(path, bytes.length));
        sftp.contentsByPath.put(path, bytes);
    }

    private static byte[] buildXlsx(String header, String... dataRows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("data");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue(header);
            for (int i = 0; i < dataRows.length; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(dataRows[i]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (java.io.IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Fake SFTP port — no real SSH server (D-SFTP-TEST). */
    private static final class FakeSftpClientPort implements SftpClientPort {
        final Map<String, List<RemoteFile>> filesByDirectory = new LinkedHashMap<>();
        final Map<String, byte[]> contentsByPath = new LinkedHashMap<>();
        final List<String> downloaded = new ArrayList<>();
        final List<String> archived = new ArrayList<>();
        SftpConnectionConfig lastConnectionConfig;
        String lastArchiveDirectory;
        private RuntimeException listingFailure;

        void failListingWith(RuntimeException failure) {
            this.listingFailure = failure;
        }

        @Override
        public List<RemoteFile> listFiles(SftpConnectionConfig config, String remoteDirectory, String filenamePattern) {
            lastConnectionConfig = config;
            if (listingFailure != null) {
                throw listingFailure;
            }
            return filesByDirectory.getOrDefault(remoteDirectory, List.of());
        }

        @Override
        public byte[] download(SftpConnectionConfig config, String remotePath, long maxBytes) {
            downloaded.add(remotePath);
            byte[] content = contentsByPath.getOrDefault(remotePath, new byte[0]);
            if (content.length > maxBytes) {
                throw new be.myrmacta.domain.connector.model.SftpFileTooLargeException(
                        "File " + remotePath + " exceeds maxFileBytes while downloading "
                                + "(the server reported a smaller size when listing)");
            }
            return content;
        }

        @Override
        public void archive(SftpConnectionConfig config, String remotePath, String archiveDirectory) {
            archived.add(remotePath);
            lastArchiveDirectory = archiveDirectory;
        }
    }
}
