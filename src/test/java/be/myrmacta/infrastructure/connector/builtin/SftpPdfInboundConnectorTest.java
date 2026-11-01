package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
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
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    /** Serveur SFTP en mémoire : liste par motif glob, téléchargement borné, déplacement dans un dossier. */
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
            var matcher = FileSystems.getDefault().getPathMatcher("glob:" + filenamePattern);
            List<RemoteFile> found = new ArrayList<>();
            files.forEach((path, content) -> {
                Path file = Path.of(path);
                if (file.getParent().toString().equals(remoteDirectory) && matcher.matches(file.getFileName())) {
                    found.add(new RemoteFile(path, sizes.getOrDefault(path, (long) content.length)));
                }
            });
            return found;
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
            files.put(archiveDirectory + "/" + Path.of(remotePath).getFileName(), content);
        }
    }
}
