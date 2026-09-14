package com.flowforge.infrastructure.connector.sftp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.connector.model.SftpConnectionConfig;
import com.flowforge.domain.connector.model.SftpConnectionException;
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
            new SftpConnectionConfig("127.0.0.1", 1, "user", "password", null, "SHA256:unused");

    @Test
    void listFiles_connectionFailure_wrapsAsSftpConnectionException() {
        assertThatThrownBy(() -> adapter.listFiles(UNREACHABLE, "/in", "*.csv"))
                .isInstanceOf(SftpConnectionException.class);
    }

    @Test
    void download_connectionFailure_wrapsAsSftpConnectionException() {
        assertThatThrownBy(() -> adapter.download(UNREACHABLE, "/in/a.csv"))
                .isInstanceOf(SftpConnectionException.class);
    }

    @Test
    void archive_connectionFailure_wrapsAsSftpConnectionException() {
        assertThatThrownBy(() -> adapter.archive(UNREACHABLE, "/in/a.csv", "/archive"))
                .isInstanceOf(SftpConnectionException.class);
    }
}
