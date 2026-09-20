package com.flowforge.infrastructure.connector.sftp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.connector.model.SftpConnectionConfig;
import com.flowforge.domain.connector.model.SftpConnectionException;
import com.flowforge.domain.connector.model.SftpFileTooLargeException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * No real SFTP server (D-SFTP-TEST = mocké uniquement, requirements) — only the
 * connection-failure wrapping is exercised here (fast: connection refused on an unused local
 * port, no network round-trip). Behavior against a real server is proven indirectly through
 * {@code BatchImportConnectorTest} against the {@code SftpClientPort} contract this adapter
 * implements.
 */
class SshjSftpClientAdapterTest {

    private final SshjSftpClientAdapter adapter = new SshjSftpClientAdapter();

    // Port 1 is a well-known privileged port with nothing listening in any CI/dev environment —
    // connection is refused immediately, no timeout wait.
    private static final SftpConnectionConfig UNREACHABLE =
            new SftpConnectionConfig("127.0.0.1", 1, "user", "password", null, "PASSWORD", "SHA256:unused");

    @Test
    void listFiles_connectionFailure_wrapsAsSftpConnectionException() {
        assertThatThrownBy(() -> adapter.listFiles(UNREACHABLE, "/in", "*.csv"))
                .isInstanceOf(SftpConnectionException.class);
    }

    @Test
    void download_connectionFailure_wrapsAsSftpConnectionException() {
        assertThatThrownBy(() -> adapter.download(UNREACHABLE, "/in/a.csv", 1_000L))
                .isInstanceOf(SftpConnectionException.class);
    }

    @Test
    void archive_connectionFailure_wrapsAsSftpConnectionException() {
        assertThatThrownBy(() -> adapter.archive(UNREACHABLE, "/in/a.csv", "/archive"))
                .isInstanceOf(SftpConnectionException.class);
    }

    // --- readBounded (revue adversariale, connecteurs-cible lot B1 : TOCTOU sur maxFileBytes) ---
    //
    // Un serveur SFTP peut annoncer une taille au listing puis servir davantage d'octets réels au
    // download — readBounded doit détecter ce dépassement sans jamais matérialiser plus que
    // maxBytes + 1 octets en mémoire (pas de readAllBytes() sans limite). Testé directement en
    // isolation sur un InputStream simple : pas besoin d'un vrai serveur SSH (D-SFTP-TEST).

    @Test
    void readBounded_streamWithinLimit_returnsAllBytes() throws Exception {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] result = SshjSftpClientAdapter.readBounded(new ByteArrayInputStream(data), 10L, "/in/a.csv");
        assertThat(result).isEqualTo(data);
    }

    @Test
    void readBounded_streamExceedingLimit_throwsWithoutBufferingTheWholeStream() {
        byte[] data = "this is way more than the limit allows".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() ->
                        SshjSftpClientAdapter.readBounded(new ByteArrayInputStream(data), 4L, "/in/lying.csv"))
                .isInstanceOf(SftpFileTooLargeException.class)
                .hasMessageContaining("/in/lying.csv");
    }
}
