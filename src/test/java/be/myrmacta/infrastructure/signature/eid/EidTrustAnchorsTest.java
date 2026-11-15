package be.myrmacta.infrastructure.signature.eid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** D-EID-TRUST : le fichier PEM des autorités de confiance est obligatoire et lu au démarrage. */
class EidTrustAnchorsTest {

    private static final String DIR = "src/test/resources/eid/";

    @Test
    void readsSeveralAuthoritiesFromOnePemFile() {
        EidTrustAnchors anchors = EidTrustAnchors.load(DIR + "test-anchors.pem");

        assertThat(anchors.certificates()).hasSize(2);
        assertThat(anchors.certificates().get(0).getSubjectX500Principal().getName()).contains("Root CA 1");
        assertThat(anchors.certificates().get(1).getSubjectX500Principal().getName()).contains("Root CA 2");
    }

    @Test
    void readsASingleAuthority(@TempDir Path dir) throws Exception {
        String first = Files.readString(Path.of(DIR + "test-anchors.pem")).split("(?<=-----END CERTIFICATE-----\\n)")[0];
        Path file = dir.resolve("one.pem");
        Files.writeString(file, first);

        assertThat(EidTrustAnchors.load(file.toString()).certificates()).hasSize(1);
    }

    @Test
    void refusesAMissingOrBlankPathWithAClearMessage() {
        assertThatThrownBy(() -> EidTrustAnchors.load(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(EidTrustAnchors.PROPERTY);
        assertThatThrownBy(() -> EidTrustAnchors.load("  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(EidTrustAnchors.PROPERTY);
    }

    @Test
    void refusesAFileThatDoesNotExistOrIsADirectory(@TempDir Path dir) {
        assertThatThrownBy(() -> EidTrustAnchors.load(dir.resolve("absent.pem").toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no such file");
        assertThatThrownBy(() -> EidTrustAnchors.load(dir.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no such file");
    }

    @Test
    void refusesAFileWithoutAnyCertificate() {
        assertThatThrownBy(() -> EidTrustAnchors.load(DIR + "empty.pem"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(EidTrustAnchors.PROPERTY);
        assertThatThrownBy(() -> EidTrustAnchors.load(DIR + "not-a-certificate.pem"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(EidTrustAnchors.PROPERTY);
    }
}
