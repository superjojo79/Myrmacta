package be.myrmacta.infrastructure.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.promotion.model.ArtifactType;
import be.myrmacta.domain.promotion.model.BundleEntry;
import be.myrmacta.domain.promotion.model.ConfigurationBundle;
import be.myrmacta.domain.promotion.model.InvalidBundleException;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.DossierTypeDoc;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/** TODO §83 (D4) — l'archive : écriture déterministe, lecture défensive (altération, chemins, tailles, format). */
class ConfigurationBundleCodecTest {

    private final ZipConfigurationBundleCodec zip = new ZipConfigurationBundleCodec();
    private final JsonArtifactCodec json = new JsonArtifactCodec();

    private ConfigurationBundle sample() {
        return new ConfigurationBundle(1, Instant.parse("2026-10-01T10:00:00Z"), "recette", "0.1", List.of(
                new BundleEntry(ArtifactType.DOSSIER_TYPE, "T1", json.encode(new DossierTypeDoc("T1", "L", "wf", true, false,
                        List.of(), null, false, null, List.of()))),
                new BundleEntry(ArtifactType.DMN, "grid", "<dmn/>".getBytes(StandardCharsets.UTF_8)),
                new BundleEntry(ArtifactType.BPMN, "flow", "<bpmn/>".getBytes(StandardCharsets.UTF_8))));
    }

    /** Fabrique une archive à la main pour tester la lecture, y compris avec des chemins hostiles. */
    private static byte[] craft(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (var f : files.entrySet()) {
                z.putNextEntry(new ZipEntry(f.getKey()));
                z.write(f.getValue());
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static String manifest(String formatVersion, String artifacts) {
        return "{\"formatVersion\":" + formatVersion + ",\"generatedAt\":\"2026-10-01T10:00:00Z\",\"sourceLabel\":\"x\",\"appVersion\":\"\",\"artifacts\":"
                + artifacts + "}";
    }

    private static String artifact(String type, String key, String path, byte[] content) {
        return "{\"type\":\"" + type + "\",\"key\":\"" + key + "\",\"path\":\"" + path + "\",\"sha256\":\"" + BundleEntry.sha256(content) + "\"}";
    }

    @Test
    void aBundleRoundTripsAndTheArchiveIsDeterministic() {
        ConfigurationBundle bundle = sample();
        byte[] a = zip.write(bundle);
        byte[] b = zip.write(bundle);
        assertThat(a).as("même bundle, mêmes octets (versionnable)").isEqualTo(b);

        ConfigurationBundle read = zip.read(a);

        assertThat(read.sourceLabel()).isEqualTo("recette");
        assertThat(read.generatedAt()).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
        assertThat(read.entries()).containsExactlyInAnyOrderElementsOf(bundle.entries());
        assertThat(read.sha256()).isEqualTo(bundle.sha256());
    }

    @Test
    void theJsonEncodingIsCanonicalAndStrict() {
        var doc = new DossierTypeDoc("T1", "L", "wf", true, false, List.of(), null, false, null, List.of());
        assertThat(json.encode(doc)).isEqualTo(json.encode(json.decode(json.encode(doc), DossierTypeDoc.class)));
        assertThat(new String(json.encode(doc), StandardCharsets.UTF_8)).endsWith("}\n");
        assertThatThrownBy(() -> json.decode("{\"code\":\"T\",\"surprise\":1}".getBytes(StandardCharsets.UTF_8), DossierTypeDoc.class))
                .isInstanceOf(InvalidBundleException.class);
        assertThatThrownBy(() -> json.decode("not json".getBytes(StandardCharsets.UTF_8), DossierTypeDoc.class))
                .isInstanceOf(InvalidBundleException.class);
    }

    @Test
    void aFileAlteredAfterExportIsDetected() throws Exception {
        byte[] original = "<dmn/>".getBytes(StandardCharsets.UTF_8);
        byte[] archive = craft(Map.of(
                "manifest.json", manifest("1", "[" + artifact("DMN", "grid", "dmn/grid.dmn", original) + "]").getBytes(StandardCharsets.UTF_8),
                "dmn/grid.dmn", "<dmn evil='1'/>".getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> zip.read(archive)).isInstanceOf(InvalidBundleException.class).hasMessageContaining("checksum");
    }

    @Test
    void hostilePathsAreNeverFollowed() throws Exception {
        byte[] content = "x".getBytes(StandardCharsets.UTF_8);
        for (String path : List.of("../evil.dmn", "/etc/passwd", "dmn/../../x.dmn", "dmn/grid.dmn/../../y")) {
            byte[] archive = craft(Map.of(
                    "manifest.json", manifest("1", "[" + artifact("DMN", "grid", path, content) + "]").getBytes(StandardCharsets.UTF_8),
                    path, content));
            assertThatThrownBy(() -> zip.read(archive)).as(path).isInstanceOf(InvalidBundleException.class);
        }
        // une clé hostile dans le manifeste
        byte[] archive = craft(Map.of(
                "manifest.json", manifest("1", "[" + artifact("DMN", "../x", "dmn/../x.dmn", content) + "]").getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> zip.read(archive)).isInstanceOf(InvalidBundleException.class);
    }

    @Test
    void anUndeclaredFileAMissingFileAndAnUnknownFormatAreRefused() throws Exception {
        byte[] content = "<dmn/>".getBytes(StandardCharsets.UTF_8);
        String listed = "[" + artifact("DMN", "grid", "dmn/grid.dmn", content) + "]";
        assertThatThrownBy(() -> zip.read(craft(Map.of("manifest.json", manifest("1", listed).getBytes(StandardCharsets.UTF_8),
                "dmn/grid.dmn", content, "dmn/smuggled.dmn", content))))
                .isInstanceOf(InvalidBundleException.class).hasMessageContaining("not declared");
        assertThatThrownBy(() -> zip.read(craft(Map.of("manifest.json", manifest("1", listed).getBytes(StandardCharsets.UTF_8)))))
                .isInstanceOf(InvalidBundleException.class).hasMessageContaining("missing");
        assertThatThrownBy(() -> zip.read(craft(Map.of("manifest.json", manifest("9", listed).getBytes(StandardCharsets.UTF_8),
                "dmn/grid.dmn", content))))
                .isInstanceOf(InvalidBundleException.class).hasMessageContaining("unsupported");
        assertThatThrownBy(() -> zip.read(craft(Map.of("dmn/grid.dmn", content)))).hasMessageContaining("manifest");
        assertThatThrownBy(() -> zip.read(craft(Map.of("manifest.json", "{ nope".getBytes(StandardCharsets.UTF_8)))))
                .isInstanceOf(InvalidBundleException.class);
    }

    @Test
    void anOversizedFileInTheArchiveIsRefusedWhileReading() throws Exception {
        byte[] big = new byte[ConfigurationBundle.MAX_ENTRY_BYTES + 1];
        byte[] archive = craft(Map.of(
                "manifest.json", manifest("1", "[" + artifact("DMN", "grid", "dmn/grid.dmn", big) + "]").getBytes(StandardCharsets.UTF_8),
                "dmn/grid.dmn", big));
        assertThatThrownBy(() -> zip.read(archive)).isInstanceOf(InvalidBundleException.class).hasMessageContaining("exceeds");
    }

    @Test
    void aDuplicateInTheManifestIsRefused() throws Exception {
        byte[] content = "<dmn/>".getBytes(StandardCharsets.UTF_8);
        String a = artifact("DMN", "grid", "dmn/grid.dmn", content);
        assertThatThrownBy(() -> zip.read(craft(Map.of("manifest.json", manifest("1", "[" + a + "," + a + "]").getBytes(StandardCharsets.UTF_8),
                "dmn/grid.dmn", content))))
                .isInstanceOf(InvalidBundleException.class);
    }
}
