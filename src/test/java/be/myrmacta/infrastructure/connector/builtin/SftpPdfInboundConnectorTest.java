package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.PollingTriggerConnector;
import be.myrmacta.domain.connector.model.SftpConnectionConfig;
import be.myrmacta.domain.connector.model.SftpFileTooLargeException;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.domain.connector.port.out.SftpClientPort;
import be.myrmacta.infrastructure.config.InboundConnectorProperties;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.json.JsonMapper;

/** TODO §113 (D-MAILROOM-PDF-SFTP) — un courrier par PDF, remis avant rangement ; index JSON compagnon ou CSV par lot. */
class SftpPdfInboundConnectorTest {

    private static final byte[] PDF = "%PDF-1.4 courrier".getBytes(StandardCharsets.US_ASCII);

    private final FakeSftp sftp = new FakeSftp();
    private final List<WorkflowTriggerEvent> delivered = new ArrayList<>();
    private SftpPdfInboundConnector connector;

    @BeforeEach
    void setUp() {
        connector = new SftpPdfInboundConnector(sftp, new SimpleJsonPathMapper(), JsonMapper.builder().build(),
                InboundConnectorProperties.defaults());
    }

    @Test
    void aPdfWithItsJsonCompanionBecomesAMail_andBothMoveToProcessed() {
        sftp.put("/in/facture-117.pdf", PDF);
        sftp.put("/in/facture-117.json", "{\"expediteur\":\"compta@fournisseur.example\",\"objet\":\"Facture 117\"}");

        connector.poll(instance(config("JSON")), delivered::add);

        assertThat(delivered).singleElement().satisfies(event -> {
            assertThat(event.processKey()).isEqualTo("triCourrier");
            assertThat(event.dossierTypeCode()).isEqualTo("COURRIER");
            assertThat(event.dossierFields()).containsEntry("expediteur", "compta@fournisseur.example")
                    .containsEntry("objet", "Facture 117").containsEntry("canal", "PAPIER");
            assertThat(event.variables()).containsEntry("courrierFichier", "facture-117.pdf");
            assertThat(event.attachments()).singleElement().satisfies(attachment -> {
                assertThat(attachment.filename()).isEqualTo("facture-117.pdf");
                assertThat(attachment.contentType()).isEqualTo("application/pdf");
            });
            assertThat(event.createWhenReferenceUnknown()).isTrue();
        });
        assertThat(sftp.files.keySet()).containsExactlyInAnyOrder("/done/facture-117.pdf", "/done/facture-117.json");
    }

    @Test
    void withoutItsCompanionAPdfWaits_unlessTheIndexIsOptional() {
        sftp.put("/in/scan-1.pdf", PDF);

        connector.poll(instance(config("JSON")), delivered::add);
        assertThat(delivered).isEmpty();
        assertThat(sftp.files).containsKey("/in/scan-1.pdf");

        Map<String, Object> optional = config("JSON");
        optional.put("requireIndex", false);
        optional.put("defaultFields", Map.of("canal", "PAPIER", "expediteur", "inconnu", "objet", "Courrier papier"));
        connector.poll(instance(optional), delivered::add);
        assertThat(delivered).singleElement().satisfies(event -> assertThat(event.dossierFields())
                .containsEntry("objet", "Courrier papier").containsEntry("expediteur", "inconnu"));
        assertThat(sftp.files).containsOnlyKeys("/done/scan-1.pdf");
    }

    @Test
    void aFileThatIsNotAPdf_anEmptyMappedField_anUnreadableIndex_orAFailedDeliveryGoToRejected() {
        sftp.put("/in/a.pdf", "MZ pas un pdf".getBytes(StandardCharsets.US_ASCII));
        sftp.put("/in/a.json", "{\"expediteur\":\"x@y.example\",\"objet\":\"A\"}");
        sftp.put("/in/b.pdf", PDF);
        sftp.put("/in/b.json", "{\"expediteur\":\"x@y.example\"}");
        sftp.put("/in/c.pdf", PDF);
        sftp.put("/in/c.json", "[1, 2]");
        sftp.put("/in/d.pdf", PDF);
        sftp.put("/in/d.json", "{\"expediteur\":\"x@y.example\",\"objet\":\"D\"}");

        connector.poll(instance(config("JSON")), event -> {
            throw new IllegalStateException("db down");
        });

        assertThat(sftp.files.keySet()).containsExactlyInAnyOrder("/ko/a.pdf", "/ko/a.json", "/ko/b.pdf", "/ko/b.json", "/ko/c.pdf",
                "/ko/c.json", "/ko/d.pdf", "/ko/d.json");
    }

    /**
     * Issue #277 — un PDF écarté par les contrôles des pièces (antivirus, type, contenu, stockage) ne devient pas un courrier : sa
     * remise est annulée et il est rangé dans « refusés », avec son index, comme à l'écran « Déposer ».
     */
    @Test
    void aPdfRejectedByTheAttachmentChecksCreatesNoMail_andGoesToRejected() {
        RecordingTransactions transactions = new RecordingTransactions();
        connector = new SftpPdfInboundConnector(sftp, new SimpleJsonPathMapper(), JsonMapper.builder().build(),
                InboundConnectorProperties.defaults(), transactions);
        sftp.put("/in/infecte.pdf", PDF);
        sftp.put("/in/infecte.json", "{\"expediteur\":\"x@y.example\",\"objet\":\"Infecté\"}");
        sftp.put("/in/sain.pdf", PDF);
        sftp.put("/in/sain.json", "{\"expediteur\":\"x@y.example\",\"objet\":\"Sain\"}");

        connector.poll(instance(config("JSON")), new PollingTriggerConnector.EventDelivery() {
            @Override
            public void deliver(WorkflowTriggerEvent event) {
                throw new AssertionError("the SFTP connector needs the outcome of the delivery");
            }

            @Override
            public Outcome deliverAndReport(WorkflowTriggerEvent event) {
                delivered.add(event);
                return new Outcome(event.variables().get("courrierFichier").equals("infecte.pdf") ? 1 : 0);
            }
        });

        assertThat(delivered).hasSize(2);
        assertThat(transactions.rolledBack).containsExactly(true, false);
        assertThat(sftp.files.keySet()).containsExactlyInAnyOrder("/ko/infecte.pdf", "/ko/infecte.json", "/done/sain.pdf",
                "/done/sain.json");
    }

    /** Une transaction par remise : enregistre si elle a été annulée. */
    private static final class RecordingTransactions implements TransactionOperations {

        private final List<Boolean> rolledBack = new ArrayList<>();

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            SimpleTransactionStatus status = new SimpleTransactionStatus(true);
            try {
                return action.doInTransaction(status);
            } finally {
                rolledBack.add(status.isRollbackOnly());
            }
        }
    }

    @Test
    void aCsvIndexDescribesABatch_aLineWithoutItsPdfIsIgnored_andAnUnlistedPdfWaits() {
        sftp.put("/in/lot-1.pdf", PDF);
        sftp.put("/in/lot-2.pdf", PDF);
        sftp.put("/in/autre.pdf", PDF);
        sftp.put("/in/lot.csv", "fichier,expediteur,objet\nlot-1.pdf,a@b.example,Premier\nlot-3.pdf,c@d.example,Absent\n"
                + "lot-2.pdf,e@f.example,Second\n");

        connector.poll(instance(config("CSV")), delivered::add);

        assertThat(delivered).extracting(event -> event.dossierFields().get("objet")).containsExactly("Premier", "Second");
        assertThat(sftp.files.keySet()).containsExactlyInAnyOrder("/done/lot-1.pdf", "/done/lot-2.pdf", "/done/lot.csv", "/in/autre.pdf");
    }

    /** Revue de clôture : le plafond par interrogation vaut aussi pour un index CSV ; un CSV entamé reste en place et se termine ensuite. */
    @Test
    void aCsvIndexRespectsThePerPollCap_andIsArchivedOnceAllItsLinesAreDone() {
        connector = new SftpPdfInboundConnector(sftp, new SimpleJsonPathMapper(), JsonMapper.builder().build(),
                new InboundConnectorProperties(2, 26214400, 20000, 10485760, 30, 1048576));
        StringBuilder csv = new StringBuilder("fichier,expediteur,objet\n");
        for (int i = 1; i <= 3; i++) {
            sftp.put("/in/p" + i + ".pdf", PDF);
            csv.append("p").append(i).append(".pdf,a@b.example,Objet ").append(i).append('\n');
        }
        sftp.put("/in/lot.csv", csv.toString());

        connector.poll(instance(config("CSV")), delivered::add);
        assertThat(delivered).hasSize(2);
        assertThat(sftp.files).containsKeys("/in/lot.csv", "/in/p3.pdf");

        connector.poll(instance(config("CSV")), delivered::add);
        assertThat(delivered).extracting(event -> event.dossierFields().get("objet")).containsExactly("Objet 1", "Objet 2", "Objet 3");
        assertThat(sftp.files.keySet()).containsExactlyInAnyOrder("/done/p1.pdf", "/done/p2.pdf", "/done/p3.pdf", "/done/lot.csv");
    }

    @Test
    void anIndexTooLargeIsNeverDownloaded() {
        sftp.put("/in/e.pdf", PDF);
        sftp.put("/in/e.json", "{}");
        sftp.sizes.put("/in/e.json", SftpPdfInboundConnector.MAX_INDEX_BYTES + 1);

        connector.poll(instance(config("JSON")), delivered::add);

        assertThat(delivered).isEmpty();
        assertThat(sftp.downloads).doesNotContain("/in/e.json");
        assertThat(sftp.files.keySet()).containsExactlyInAnyOrder("/ko/e.pdf", "/ko/e.json");
    }

    @Test
    void theConfigurationIsChecked() {
        connector.validate(config("JSON"));
        Map<String, Object> same = config("JSON");
        same.put("processedDirectory", "/in");
        assertThatThrownBy(() -> connector.validate(same)).isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("must differ");
        Map<String, Object> format = config("XML");
        assertThatThrownBy(() -> connector.validate(format)).hasMessageContaining("indexFormat");
        Map<String, Object> noFingerprint = config("JSON");
        noFingerprint.remove("sftpHostKeyFingerprint");
        assertThatThrownBy(() -> connector.validate(noFingerprint)).hasMessageContaining("sftpHostKeyFingerprint");
        Map<String, Object> mapping = config("JSON");
        mapping.put("mapping", "pas un objet");
        assertThatThrownBy(() -> connector.validate(mapping)).hasMessageContaining("mapping must be a JSON object");
    }

    @Test
    void theConnectorDescribesItself() {
        assertThat(connector.getType()).isEqualTo("sftp-pdf-inbound");
        assertThat(connector.getLabel()).contains("SFTP");
        assertThat(connector.getCapability()).isEqualTo(ConnectorCapability.TRIGGER);
        assertThat(connector.getConfigSchema()).extracting(ConfigField::name).contains("remoteDirectory", "processedDirectory",
                "rejectedDirectory", "indexFormat", "requireIndex", "mapping");
    }

    @Test
    void theAuthenticationAndThePollIntervalAreChecked() {
        assertThatThrownBy(() -> connector.validate(null)).hasMessageContaining("config is required");
        Map<String, Object> privateKey = config("JSON");
        privateKey.put("sftpAuthType", "PRIVATE_KEY");
        privateKey.put("sftpPrivateKey", "-----BEGIN OPENSSH PRIVATE KEY-----");
        connector.validate(privateKey);
        privateKey.remove("sftpPrivateKey");
        assertThatThrownBy(() -> connector.validate(privateKey)).hasMessageContaining("sftpPrivateKey is required");
        Map<String, Object> token = config("JSON");
        token.put("sftpAuthType", "TOKEN");
        assertThatThrownBy(() -> connector.validate(token)).hasMessageContaining("Unsupported sftpAuthType");
        Map<String, Object> poll = config("JSON");
        poll.put("pollSeconds", 30);
        connector.validate(poll);
        poll.put("pollSeconds", 29);
        assertThatThrownBy(() -> connector.validate(poll)).hasMessageContaining("pollSeconds must be at least 30");
    }

    /** Sans index : chaque PDF est traité aussitôt, dans l'ordre des noms, dans la limite du plafond par interrogation. */
    @Test
    void withoutAnIndexThePdfsAreTakenInNameOrder_upToThePerPollCap() {
        connector = cappedAt(2);
        for (int i = 1; i <= 3; i++) {
            sftp.put("/in/p" + i + ".pdf", PDF);
        }

        connector.poll(instance(unmapped("NONE")), delivered::add);

        assertThat(delivered).extracting(event -> event.variables().get("courrierFichier")).containsExactly("p1.pdf", "p2.pdf");
        assertThat(sftp.files.keySet()).containsExactlyInAnyOrder("/done/p1.pdf", "/done/p2.pdf", "/in/p3.pdf");
    }

    @Test
    void theRootDirectoryCanBeRead() {
        Map<String, Object> config = unmapped("NONE");
        config.put("remoteDirectory", "/");
        sftp.put("/scan.pdf", PDF);

        connector.poll(instance(config), delivered::add);

        assertThat(delivered).singleElement().satisfies(event -> assertThat(event.variables()).containsEntry("courrierFichier", "scan.pdf"));
        assertThat(sftp.files.keySet()).containsExactly("/done/scan.pdf");
    }

    /** Le contenu doit commencer par la signature « %PDF- » : un fichier plus court ou d'un autre type n'est jamais remis. */
    @Test
    void onlyAContentStartingWithThePdfSignatureIsDelivered() {
        sftp.put("/in/court.pdf", "%PDF".getBytes(StandardCharsets.US_ASCII));
        sftp.put("/in/exe.pdf", "MZ pas un pdf".getBytes(StandardCharsets.US_ASCII));
        sftp.put("/in/juste.pdf", "%PDF-".getBytes(StandardCharsets.US_ASCII));

        connector.poll(instance(unmapped("NONE")), delivered::add);

        assertThat(delivered).extracting(event -> event.variables().get("courrierFichier")).containsExactly("juste.pdf");
        assertThat(sftp.files.keySet()).containsExactlyInAnyOrder("/ko/court.pdf", "/ko/exe.pdf", "/done/juste.pdf");
    }

    /** Un index JSON trop gros, qui n'est pas un objet ou illisible range le PDF dans « refusés », même sans champ mappé. */
    @Test
    void aJsonIndexTooLargeNotAnObjectOrUnreadableRejectsItsPdf_evenWithoutMapping() {
        sftp.put("/in/a.pdf", PDF);
        sftp.put("/in/a.json", "{}");
        sftp.sizes.put("/in/a.json", SftpPdfInboundConnector.MAX_INDEX_BYTES);
        sftp.put("/in/b.pdf", PDF);
        sftp.put("/in/b.json", "{}");
        sftp.sizes.put("/in/b.json", SftpPdfInboundConnector.MAX_INDEX_BYTES + 1);
        sftp.put("/in/c.pdf", PDF);
        sftp.put("/in/c.json", "[1]");
        sftp.put("/in/d.pdf", PDF);
        sftp.put("/in/d.json", "{\"objet\":");

        connector.poll(instance(unmapped("JSON")), delivered::add);

        assertThat(delivered).extracting(event -> event.variables().get("courrierFichier")).containsExactly("a.pdf");
        assertThat(sftp.files.keySet()).containsExactlyInAnyOrder("/done/a.pdf", "/done/a.json", "/ko/b.pdf", "/ko/b.json", "/ko/c.pdf",
                "/ko/c.json", "/ko/d.pdf", "/ko/d.json");
    }

    @Test
    void withJsonCompanionsThePerPollCapApplies_withOrWithoutTheIndex() {
        connector = cappedAt(2);
        for (int i = 1; i <= 3; i++) {
            sftp.put("/in/p" + i + ".pdf", PDF);
            sftp.put("/in/p" + i + ".json", "{\"expediteur\":\"a@b.example\",\"objet\":\"Objet " + i + "\"}");
        }
        connector.poll(instance(config("JSON")), delivered::add);
        assertThat(delivered).hasSize(2);
        assertThat(sftp.files).containsOnlyKeys("/done/p1.pdf", "/done/p1.json", "/done/p2.pdf", "/done/p2.json", "/in/p3.pdf",
                "/in/p3.json");

        sftp.files.clear();
        delivered.clear();
        for (int i = 1; i <= 3; i++) {
            sftp.put("/in/s" + i + ".pdf", PDF);
        }
        Map<String, Object> optional = unmapped("JSON");
        optional.put("requireIndex", false);
        connector.poll(instance(optional), delivered::add);
        assertThat(delivered).hasSize(2);
        assertThat(sftp.files).containsOnlyKeys("/done/s1.pdf", "/done/s2.pdf", "/in/s3.pdf");
    }

    /** Index CSV facultatif : les PDF absents de tout index sont traités après ceux de l'index, dans la limite du plafond. */
    @Test
    void withAnOptionalCsvIndexTheUnlistedPdfsFillTheRestOfTheCap() {
        connector = cappedAt(2);
        sftp.put("/in/lot-1.pdf", PDF);
        sftp.put("/in/x1.pdf", PDF);
        sftp.put("/in/x2.pdf", PDF);
        sftp.put("/in/lot.csv", "fichier,expediteur,objet\nlot-1.pdf,a@b.example,Premier\n");
        Map<String, Object> config = config("CSV");
        config.put("requireIndex", false);
        config.put("defaultFields", Map.of("expediteur", "inconnu", "objet", "Sans index"));

        connector.poll(instance(config), delivered::add);

        assertThat(delivered).extracting(event -> event.variables().get("courrierFichier")).containsExactly("lot-1.pdf", "x1.pdf");
        assertThat(sftp.files.keySet()).containsExactlyInAnyOrder("/done/lot-1.pdf", "/done/lot.csv", "/done/x1.pdf", "/in/x2.pdf");
    }

    /** Une fois le plafond atteint, l'index CSV suivant n'est pas ouvert : il reste en place pour le passage suivant. */
    @Test
    void onceTheCapIsReachedTheNextCsvIndexWaits() {
        connector = cappedAt(2);
        sftp.put("/in/p1.pdf", PDF);
        sftp.put("/in/p2.pdf", PDF);
        sftp.put("/in/a.csv", "fichier,expediteur,objet\np1.pdf,a@b.example,Un\np2.pdf,a@b.example,Deux\n");
        sftp.put("/in/b.csv", "fichier,expediteur,objet\nabsent.pdf,a@b.example,Trois\n");

        connector.poll(instance(config("CSV")), delivered::add);

        assertThat(delivered).hasSize(2);
        assertThat(sftp.files.keySet()).containsExactlyInAnyOrder("/done/p1.pdf", "/done/p2.pdf", "/done/a.csv", "/in/b.csv");
    }

    /** Un index CSV trop gros (jamais téléchargé), trop long ou illisible va dans « refusés » ; à la taille limite, il est lu. */
    @Test
    void aCsvIndexTooLargeTooLongOrUnreadableGoesToRejected() {
        String header = "fichier,expediteur,objet\n";
        sftp.put("/in/a-limite.csv", header);
        sftp.sizes.put("/in/a-limite.csv", SftpPdfInboundConnector.MAX_CSV_BYTES);
        sftp.put("/in/b-gros.csv", header);
        sftp.sizes.put("/in/b-gros.csv", SftpPdfInboundConnector.MAX_CSV_BYTES + 1);
        sftp.put("/in/c-long.csv", header + "absent.pdf,a@b.example,Objet\n".repeat(SftpPdfInboundConnector.MAX_CSV_ROWS + 1));
        sftp.put("/in/d-illisible.csv", "fichier,\"objet\nabsent.pdf,Objet\n");

        connector.poll(instance(config("CSV")), delivered::add);

        assertThat(delivered).isEmpty();
        assertThat(sftp.downloads).doesNotContain("/in/b-gros.csv");
        assertThat(sftp.files.keySet()).containsExactlyInAnyOrder("/done/a-limite.csv", "/ko/b-gros.csv", "/ko/c-long.csv",
                "/ko/d-illisible.csv");
    }

    private SftpPdfInboundConnector cappedAt(int maxMessagesPerPoll) {
        return new SftpPdfInboundConnector(sftp, new SimpleJsonPathMapper(), JsonMapper.builder().build(),
                new InboundConnectorProperties(maxMessagesPerPoll, 26214400, 20000, 10485760, 30, 1048576));
    }

    private static ConnectorInstanceData instance(Map<String, Object> config) {
        return new ConnectorInstanceData(UUID.randomUUID(), "tenant_dev", "Scans", "sftp-pdf-inbound", true, config);
    }

    private static Map<String, Object> config(String indexFormat) {
        Map<String, Object> config = new HashMap<>();
        config.put("sftpHost", "sftp.example.com");
        config.put("sftpUsername", "scan");
        config.put("sftpAuthType", "PASSWORD");
        config.put("sftpPassword", "secret");
        config.put("sftpHostKeyFingerprint", "SHA256:abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG");
        config.put("remoteDirectory", "/in");
        config.put("processedDirectory", "/done");
        config.put("rejectedDirectory", "/ko/");
        config.put("indexFormat", indexFormat);
        config.put("processKey", "triCourrier");
        config.put("dossierTypeCode", "COURRIER");
        config.put("mapping", Map.of("fields", Map.of("expediteur", "$.index.expediteur", "objet", "$.index.objet")));
        config.put("defaultFields", Map.of("canal", "PAPIER"));
        return config;
    }

    /** Sans mapping ni valeur par défaut : le courrier ne dépend que du PDF. */
    private static Map<String, Object> unmapped(String indexFormat) {
        Map<String, Object> config = config(indexFormat);
        config.remove("mapping");
        config.remove("defaultFields");
        return config;
    }

    /**
     * Serveur SFTP en mémoire : liste par motif glob (sans ordre garanti : ici, à rebours), téléchargement borné, déplacement dans un
     * dossier.
     */
    private static final class FakeSftp implements SftpClientPort {

        private final Map<String, byte[]> files = new TreeMap<>();
        private final Map<String, Long> sizes = new HashMap<>();
        private final List<String> downloads = new ArrayList<>();

        void put(String path, byte[] content) {
            files.put(path, content);
        }

        void put(String path, String content) {
            files.put(path, content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public List<RemoteFile> listFiles(SftpConnectionConfig config, String remoteDirectory, String filenamePattern) {
            Objects.requireNonNull(config, "config");
            var matcher = FileSystems.getDefault().getPathMatcher("glob:" + filenamePattern);
            List<RemoteFile> found = new ArrayList<>();
            files.forEach((path, content) -> {
                // Chemin distant toujours en « / » : Path.of(path).getParent() donnerait « \in » sous Windows.
                String name = SftpPdfInboundConnector.fileName(path);
                String parent = path.substring(0, path.length() - name.length() - 1);
                if ((parent.isEmpty() ? "/" : parent).equals(remoteDirectory) && matcher.matches(Path.of(name))) {
                    found.add(new RemoteFile(path, sizes.getOrDefault(path, (long) content.length)));
                }
            });
            return found.reversed();
        }

        @Override
        public byte[] download(SftpConnectionConfig config, String remotePath, long maxBytes) {
            downloads.add(remotePath);
            byte[] content = files.get(remotePath);
            if (content.length > maxBytes) {
                throw new SftpFileTooLargeException(remotePath);
            }
            return content;
        }

        @Override
        public void archive(SftpConnectionConfig config, String remotePath, String archiveDirectory) {
            byte[] content = files.remove(remotePath);
            files.put(archiveDirectory + "/" + SftpPdfInboundConnector.fileName(remotePath), content);
        }
    }
}
